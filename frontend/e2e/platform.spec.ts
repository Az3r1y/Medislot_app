import { test, expect } from '@playwright/test';

test('console master : connexion séparée, cabinets, praticiens et vue minimisée', async ({
  page,
}) => {
  await page.goto('/platform/');
  await expect(page.getByRole('heading', { name: 'Console master' })).toBeVisible();
  await page.getByLabel('Adresse e-mail', { exact: true }).fill('master@medislot.test');
  await page.getByLabel('Mot de passe', { exact: true }).fill('MediSlot-demo-2026!');
  await page.getByRole('button', { name: 'Continuer', exact: true }).click();

  await expect(page.getByRole('heading', { name: /^Cabinets/ })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Cabinet des Tilleuls', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Cabinet des Tilleuls', exact: true }).click();
  await expect(page).toHaveURL(/\/platform\/clinics\/cabinet-a$/);

  await page
    .getByRole('link', { name: /Voir les rendez-vous de/ })
    .first()
    .click();
  await expect(page.getByText('Vue minimisée : les patients sont pseudonymisés.')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('@medislot.test');

  await page.getByRole('link', { name: 'Dashboard' }).click();
  await expect(page).toHaveURL(/from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}&clinic=all/);
  await expect(page.getByRole('heading', { name: 'Rendez-vous par cabinet' })).toBeVisible();

  await page.getByRole('button', { name: /Déconnexion/ }).click();
  await expect(page.getByRole('heading', { name: 'Console master' })).toBeVisible();
});

test('console master : un patient connecté est refusé', async ({ page }) => {
  await page.goto('/c/cabinet-a/');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await page.getByLabel('Adresse e-mail', { exact: true }).fill('patient@medislot.test');
  await page.getByLabel('Mot de passe', { exact: true }).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('button', { name: /Déconnexion/ })).toBeVisible();
  await page.goto('/platform/clinics');
  await expect(page.getByText('« Cette zone est réservée à l’équipe plateforme. »')).toBeVisible();
});
