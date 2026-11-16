import { test, expect } from '@playwright/test';

test('connexion Google optionnelle et retour d’annulation dans le cabinet choisi', async ({
  page,
  request,
}) => {
  const response = await request.get('/api/auth/providers');
  expect(response.status()).toBe(200);
  const providers = await response.json();
  await page.goto('/c/cabinet-b/');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  const google = page.getByRole('link', { name: 'Continuer avec Google' });
  if (!providers.google) {
    await expect(google).toHaveCount(0);
    return;
  }
  await expect(google).toHaveAttribute('href', '/api/oauth2/authorization/google?clinic=cabinet-b');
  const redirect = await page.request.get((await google.getAttribute('href'))!, {
    maxRedirects: 0,
  });
  expect(redirect.status()).toBe(302);
  const authorization = new URL(redirect.headers()['location']);
  expect(authorization.origin).toBe('https://accounts.google.com');
  expect(authorization.searchParams.get('code_challenge_method')).toBe('S256');
  expect(authorization.searchParams.get('nonce')).toBeTruthy();
  const state = authorization.searchParams.get('state');
  expect(state).toBeTruthy();
  await page.goto(
    `/api/login/oauth2/code/google?error=access_denied&state=${encodeURIComponent(state!)}`,
  );
  await expect(page).toHaveURL(/\/c\/cabinet-b\/$/);
  await expect(page.getByLabel('Cabinet', { exact: true })).toHaveValue('cabinet-b');
  await expect(page.getByRole('dialog')).toContainText('Connexion avec Google annulée.');
  expect(
    (await request.get('/api/oauth2/authorization/google', { maxRedirects: 0 })).status(),
  ).toBe(400);
  expect(
    (
      await request.get('/api/oauth2/authorization/google?clinic=inconnu', { maxRedirects: 0 })
    ).status(),
  ).toBe(404);
});
