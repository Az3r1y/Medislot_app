import { inject, type InjectionKey, type Ref } from 'vue';
import { ApiError } from '../api';
import type { Master } from './types';

export interface Problem {
  status: 401 | 403 | 404;
  message: string;
}
export interface Console {
  master: Ref<Master | null>;
  // Erreur de périmètre (401, 403, 404) : écran d'erreur commun. Renvoie false pour les autres
  // erreurs, que l'écran affiche lui-même.
  fail: (error: unknown) => boolean;
  notify: (message: string) => void;
}
export const CONSOLE: InjectionKey<Console> = Symbol('console');

export function useConsole(): Console {
  const value = inject(CONSOLE);
  if (!value) throw new Error('Console master absente.');
  return value;
}

// Même composant d'erreur partout, message tiré de la réponse du back quand il existe.
export function problemOf(error: unknown): Problem | null {
  if (!(error instanceof ApiError)) return null;
  if (error.status === 401)
    return { status: 401, message: 'Votre session a expiré. Reconnectez-vous.' };
  if (error.status === 403)
    return { status: 403, message: 'Cette zone est réservée à l’équipe plateforme.' };
  if (error.status === 404)
    return { status: 404, message: error.message || 'Ce cabinet n’existe pas ou a été supprimé.' };
  return null;
}

export const messageOf = (error: unknown) =>
  error instanceof ApiError
    ? error.message
    : error instanceof DOMException && error.name === 'TimeoutError'
      ? 'Le serveur ne répond pas. Réessayez dans un instant.'
      : 'Le service est momentanément indisponible.';
