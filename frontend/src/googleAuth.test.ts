import { describe, expect, test } from 'vitest';
import { GOOGLE_ERROR_MESSAGES, takeGoogleError } from './googleAuth';

describe('takeGoogleError', () => {
  test('sans paramètre : rien à afficher', () => {
    expect(takeGoogleError('http://localhost:8088/?q=cardio')).toBeNull();
  });

  test('code connu : message dédié et paramètre retiré', () => {
    expect(takeGoogleError('http://localhost:8088/?google_error=account_exists')).toEqual({
      message: GOOGLE_ERROR_MESSAGES.account_exists,
      cleanUrl: '/',
    });
  });

  test('les autres paramètres sont conservés', () => {
    expect(
      takeGoogleError('http://localhost:8088/?q=cardio&google_error=access_denied#main')?.cleanUrl,
    ).toBe('/?q=cardio#main');
  });

  test('code inconnu : message générique, jamais la valeur reçue', () => {
    const r = takeGoogleError('http://localhost:8088/?google_error=%3Cscript%3E');
    expect(r?.message).toBe(GOOGLE_ERROR_MESSAGES.failed);
  });
});
