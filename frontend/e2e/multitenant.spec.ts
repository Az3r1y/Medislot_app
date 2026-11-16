import { test, expect } from '@playwright/test';

async function login(page: import('@playwright/test').Page, email: string) {
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await page.getByLabel('Adresse e-mail', { exact: true }).fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('button', { name: /Déconnexion/ })).toBeVisible();
}

test('réserver dans A, basculer dans B et rejouer la lecture avec X-Clinic B donne 404', async ({
  page,
}) => {
  await page.goto('/c/cabinet-a/');
  await login(page, 'patient@medislot.test');
  await expect(page.locator('.clinic-banner')).toHaveCSS('border-left-color', 'rgb(23, 107, 91)');
  await page
    .getByRole('button', { name: /Réserver avec/ })
    .first()
    .click();
  const booked = page.waitForResponse(
    (r) => r.url().endsWith('/api/appointments') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: /^(Confirmer le rendez-vous|Réserver)$/ }).click();
  const appointment = await (await booked).json();
  await expect(page.getByText('Votre rendez-vous est confirmé.', { exact: true })).toBeVisible();
  try {
    const responseB = page.waitForResponse(
      (r) =>
        r.url().endsWith('/api/appointments') && r.request().headers()['x-clinic'] === 'cabinet-b',
    );
    await page.getByLabel('Cabinet', { exact: true }).selectOption('cabinet-b');
    await expect(page).toHaveURL(/\/c\/cabinet-b\/$/);
    await expect(page.getByRole('region', { name: 'Cabinet courant' })).toContainText(
      'Maison de santé du Canal',
    );
    expect(
      (await (await responseB).json()).some((a: { id: number }) => a.id === appointment.id),
    ).toBe(false);
    const replay = await page.request.get(`/api/appointments/${appointment.id}`, {
      headers: { 'X-Clinic': 'cabinet-b' },
    });
    expect(replay.status()).toBe(404);
    await expect(page.locator('.clinic-banner')).toHaveCSS('border-left-color', 'rgb(57, 85, 165)');
    await page.goBack();
    await expect(page).toHaveURL(/\/c\/cabinet-a\/$/);
    await expect(page.getByLabel('Cabinet', { exact: true })).toHaveValue('cabinet-a');
    console.log(
      `rendez-vous ${appointment.id} : créé dans A, absent de B, lecture depuis B=${replay.status()}`,
    );
  } finally {
    const csrf = await (await page.request.get('/api/csrf')).json();
    const cancelled = await page.request.post(`/api/appointments/${appointment.id}/cancel`, {
      headers: { 'X-Clinic': 'cabinet-a', [csrf.headerName]: csrf.token },
    });
    expect(cancelled.status()).toBe(200);
  }
});

test('la secrétaire de Lyon connectée depuis A est redirigée vers son seul cabinet', async ({
  page,
}) => {
  await page.goto('/c/cabinet-a/');
  await login(page, 'secretaire-b@medislot.test');
  await expect(page).toHaveURL(/\/c\/cabinet-b\/$/);
  await expect(page.getByLabel('Cabinet', { exact: true }).locator('option')).toHaveCount(1);
  await page.getByRole('button', { name: 'Agenda du cabinet' }).click();
  await expect(page.getByRole('heading', { name: 'Agenda du cabinet' })).toBeVisible();
  const denied = await page.request.get('/api/clinic/agenda', {
    headers: { 'X-Clinic': 'cabinet-a' },
  });
  expect(denied.status()).toBe(404);
});
