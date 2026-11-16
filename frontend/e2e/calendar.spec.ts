import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test('export ICS et liens personnels restent limites au cabinet courant', async ({ page }) => {
  await page.goto('/c/cabinet-a/');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await page.getByLabel('Adresse e-mail', { exact: true }).fill('patient@medislot.test');
  await page.getByLabel('Mot de passe', { exact: true }).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('button', { name: /Camille.*Déconnexion/ })).toBeVisible();
  await page
    .getByRole('button', { name: /Réserver avec/ })
    .first()
    .click();
  const booked = page.waitForResponse(
    (r) => r.url().endsWith('/api/appointments') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: /^(Confirmer le rendez-vous|Réserver)$/ }).click();
  const appointment = await (await booked).json();
  try {
    await expect(page.getByText('Votre rendez-vous est confirmé.', { exact: true })).toBeVisible();
    const downloading = page.waitForEvent('download');
    const exporting = page.waitForRequest((r) =>
      r.url().includes(`/api/appointments/${appointment.id}/calendar.ics`),
    );
    await page.getByRole('button', { name: 'Ajouter à mon agenda', exact: true }).first().click();
    const file = await downloading;
    expect(file.suggestedFilename()).toBe('rendez-vous.ics');
    expect((await exporting).headers()['x-clinic']).toBe('cabinet-a');
    expect(await readFile((await file.path())!, 'utf8')).toContain(
      `UID:appointment-${appointment.id}@medislot`,
    );
    const screenshots = process.env.CALENDAR_SCREENSHOT_DIR;
    if (screenshots)
      await page.screenshot({ path: `${screenshots}/calendar-export.png`, fullPage: true });
    await page.getByRole('button', { name: 'Créer un lien', exact: true }).click();
    const a = new URL(await page.getByLabel('Lien personnel').inputValue());
    expect(a.protocol).toMatch(/^https?:$/);
    await expect(page.getByRole('link', { name: 'Ouvrir dans mon agenda' })).toHaveAttribute(
      'href',
      /^webcal:\/\//,
    );
    if (screenshots) {
      const input = page.getByLabel('Lien personnel');
      const value = await input.inputValue();
      await input.evaluate((element: HTMLInputElement) => {
        element.value = 'Lien personnel masqué';
      });
      await page.screenshot({
        path: `${screenshots}/calendar-subscription.png`,
        fullPage: true,
      });
      await input.evaluate((element: HTMLInputElement, original) => {
        element.value = original;
      }, value);
    }
    expect(a.searchParams.get('clinic')).toBe('cabinet-a');
    const oldUrl = `/api/calendar/feed.ics${a.search}`;
    expect((await page.request.get(oldUrl)).status()).toBe(200);
    const crossed = new URL(a);
    crossed.searchParams.set('clinic', 'cabinet-b');
    expect((await page.request.get(`/api/calendar/feed.ics${crossed.search}`)).status()).toBe(404);
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Régénérer le lien' }).click();
    await expect(page.getByLabel('Lien personnel')).not.toHaveValue(a.toString());
    const updated = new URL(await page.getByLabel('Lien personnel').inputValue());
    const urlA = `/api/calendar/feed.ics${updated.search}`;
    expect((await page.request.get(oldUrl)).status()).toBe(404);
    expect((await page.request.get(urlA)).status()).toBe(200);
    await page.getByLabel('Cabinet', { exact: true }).selectOption('cabinet-b');
    await expect(page.getByLabel('Lien personnel')).toHaveCount(0);
    await page.getByRole('button', { name: 'Créer un lien', exact: true }).click();
    const b = new URL(await page.getByLabel('Lien personnel').inputValue());
    expect(b.searchParams.get('clinic')).toBe('cabinet-b');
    const urlB = `/api/calendar/feed.ics${b.search}`;
    expect((await page.request.get(urlB)).status()).toBe(200);
    await page.getByRole('button', { name: 'Révoquer le lien', exact: true }).click();
    await expect(
      page.getByRole('status').filter({ hasText: 'Votre lien d’abonnement a été révoqué.' }),
    ).toBeVisible();
    expect((await page.request.get(urlB)).status()).toBe(404);
    expect((await page.request.get(urlA)).status()).toBe(200);
  } finally {
    const csrf = await (await page.request.get('/api/csrf')).json();
    const headers = { 'X-Clinic': 'cabinet-a', [csrf.headerName]: csrf.token };
    await page.request.post(`/api/appointments/${appointment.id}/cancel`, { headers });
    await page.request.delete('/api/calendar/subscription', { headers });
  }
  await page.goto('about:blank');
});
