// Convocation PDF. Tout ce qui touche à la fenêtre passe par `browser`, remplacé dans les tests.

export const MESSAGES: Record<number, string> = {
  401: 'Votre session a expiré. Reconnectez-vous pour obtenir votre convocation.',
  404: 'Ce rendez-vous est introuvable.',
  409: 'Ce rendez-vous est annulé : il n’a plus de convocation.',
  500: 'La convocation n’a pas pu être préparée. Réessayez dans un instant.',
};
export const FALLBACK = 'La convocation est momentanément indisponible. Réessayez plus tard.';
export const OFFLINE = 'Connexion impossible. Vérifiez le réseau puis réessayez.';
export const POPUP = 'Le navigateur a bloqué l’ouverture de l’onglet. Utilisez « Enregistrer ».';

export class ConvocationError extends Error {
  constructor(
    message: string,
    public status?: number,
  ) {
    super(message);
  }
}

/** Nom proposé par le serveur, sinon un nom neutre. */
export function filenameFrom(header: string | null, id: number): string {
  return header?.match(/filename="?([^";]+)"?/)?.[1] ?? `convocation-${id}.pdf`;
}

export async function fetchConvocation(
  id: number,
  download = false,
): Promise<{ blob: Blob; filename: string }> {
  let r: Response;
  try {
    r = await fetch(`/api/appointments/${id}/convocation.pdf${download ? '?download=true' : ''}`, {
      credentials: 'same-origin',
      headers: { Accept: 'application/pdf' },
    });
  } catch {
    throw new ConvocationError(OFFLINE);
  }
  // Une erreur a aussi un corps : sans ce test, on enregistrerait du JSON sous un nom .pdf.
  if (!r.ok) throw new ConvocationError(MESSAGES[r.status] ?? FALLBACK, r.status);
  // Un 200 en HTML (repli de Nginx, portail captif) n'est pas une convocation.
  if (!(r.headers.get('Content-Type') ?? '').startsWith('application/pdf'))
    throw new ConvocationError(FALLBACK, r.status);
  return { blob: await r.blob(), filename: filenameFrom(r.headers.get('Content-Disposition'), id) };
}

export interface Tab {
  show(url: string): void;
  close(): void;
}
export interface Browser {
  openTab(): Tab | null;
  save(url: string, filename: string): void;
  later(f: () => void, ms: number): void;
  objectUrl(blob: Blob): string;
  revoke(url: string): void;
}

// Laisse au visualiseur PDF le temps de lire le blob.
const REVOKE_AFTER_MS = 60_000;

export async function openConvocation(id: number, browser: Browser = realBrowser): Promise<void> {
  // Avant tout await : hors du geste de clic, le navigateur bloquerait la fenêtre.
  const tab = browser.openTab();
  if (!tab) throw new ConvocationError(POPUP);
  try {
    const { blob } = await fetchConvocation(id);
    const url = browser.objectUrl(blob);
    tab.show(url);
    browser.later(() => browser.revoke(url), REVOKE_AFTER_MS);
  } catch (e) {
    tab.close(); // l'erreur s'affiche dans la page, pas dans un onglet vide
    throw e;
  }
}

export async function saveConvocation(id: number, browser: Browser = realBrowser): Promise<void> {
  const { blob, filename } = await fetchConvocation(id, true);
  const url = browser.objectUrl(blob);
  browser.save(url, filename);
  browser.later(() => browser.revoke(url), REVOKE_AFTER_MS);
}

export const realBrowser: Browser = {
  openTab() {
    const w = window.open('', '_blank');
    if (!w) return null;
    w.opener = null;
    return {
      show: (url) => {
        w.location.href = url;
      },
      close: () => w.close(),
    };
  },
  save(url, filename) {
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.append(a);
    a.click();
    a.remove();
  },
  later: (f, ms) => {
    setTimeout(f, ms);
  },
  objectUrl: (blob) => URL.createObjectURL(blob),
  revoke: (url) => URL.revokeObjectURL(url),
};
