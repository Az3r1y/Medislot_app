import { test, expect } from '@playwright/test';

test('quatre clics lancent la musique et le drop anime la page', async ({ page }) => {
  await page.addInitScript(() => {
    const NativeAudio = window.Audio;
    window.Audio = new Proxy(NativeAudio, {
      construct(target, args) {
        const player = Reflect.construct(target, args) as HTMLAudioElement;
        (window as Window & { testAudio?: HTMLAudioElement }).testAudio = player;
        return player;
      },
    });
  });
  await page.goto('/');
  const logo = page.getByRole('button', { name: 'MediSlot.' });
  await logo.click({ clickCount: 3 });
  await expect(page.locator('body')).not.toHaveClass(/harlem-intro|harlem-drop/);
  await logo.click();
  await expect(page.locator('body')).toHaveClass(/harlem-intro/);
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as Window & { testAudio?: HTMLAudioElement }).testAudio?.currentTime ?? 0,
      ),
    )
    .toBeGreaterThan(0);
  await page.evaluate(() => {
    (window as Window & { testAudio: HTMLAudioElement }).testAudio.currentTime = 15.7;
  });
  await expect(page.locator('body')).toHaveClass(/harlem-drop/);
  expect(await logo.evaluate((element) => getComputedStyle(element).animationName)).toBe(
    'harlem-dance',
  );
  expect(await page.evaluate(() => getComputedStyle(document.body, '::after').animationName)).toBe(
    'harlem-strobe',
  );
  await page.reload();
  await expect(page.locator('body')).not.toHaveClass(/harlem-intro|harlem-drop/);
});
