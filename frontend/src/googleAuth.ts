// Connexion avec Google : navigation complète vers le back, qui redirige vers Google.
// Après un échec, le back revient sur /?google_error=<code>.
export const GOOGLE_LOGIN_URL = '/api/oauth2/authorization/google';

export const GOOGLE_ERROR_MESSAGES: Record<string, string> = {
  account_exists:
    'Un compte MediSlot existe déjà avec cette adresse. Connectez-vous avec votre mot de passe.',
  email_not_verified:
    'Votre adresse Google n’est pas vérifiée. Utilisez un autre compte Google ou créez un compte MediSlot.',
  access_denied: 'Connexion avec Google annulée.',
  failed: 'La connexion avec Google a échoué. Réessayez.',
};

// Lit le code d'erreur et renvoie l'adresse sans ce paramètre, pour ne pas le réafficher.
export function takeGoogleError(href: string): { message: string; cleanUrl: string } | null {
  const url = new URL(href);
  const code = url.searchParams.get('google_error');
  if (code === null) return null;
  url.searchParams.delete('google_error');
  return {
    message: GOOGLE_ERROR_MESSAGES[code] ?? GOOGLE_ERROR_MESSAGES.failed,
    cleanUrl: url.pathname + url.search + url.hash,
  };
}
