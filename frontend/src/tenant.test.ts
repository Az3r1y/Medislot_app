import { describe, expect, it } from 'vitest';
import { clinicFromPath, clinicFromHost, safeColor, DEFAULT_COLOR } from './tenant';

describe('Cabinet issu de l’URL', () => {
  it('lit un chemin canonique et ses sous-pages', () => {
    expect(clinicFromPath('/c/cabinet-a/')).toBe('cabinet-a');
    expect(clinicFromPath('/c/cabinet-b/agenda')).toBe('cabinet-b');
  });
  it('rejette les chemins invalides ou les slugs encodés', () => {
    for (const path of [
      '/',
      '/prefix/c/cabinet-a/',
      '/c/../',
      '/c/A/',
      '/c/a%2Fb/',
      '/c/-a/',
      '/c/' + 'a'.repeat(64),
    ])
      expect(clinicFromPath(path)).toBeNull();
  });
  it('lit le sous-domaine exact avec port éventuel', () => {
    expect(clinicFromHost('cabinet-a.medislot.test')).toBe('cabinet-a');
    expect(clinicFromHost('cabinet-b.medislot.test:8088')).toBe('cabinet-b');
  });
  it('rejette le suffixe pirate', () => {
    expect(clinicFromHost('cabinet-a.medislot.test.pirate.example')).toBeNull();
    expect(clinicFromHost('cabinet-a.medislot.test-pirate.example')).toBeNull();
  });
  it('rejette les sous-domaines multiples et les hôtes sans cabinet', () => {
    for (const host of [
      'medislot.test',
      'localhost:8088',
      'a.b.medislot.test',
      '-a.medislot.test',
      'a.medislot.test:evil',
    ])
      expect(clinicFromHost(host)).toBeNull();
  });
  it('normalise une couleur hexadécimale à six chiffres', () => {
    expect(safeColor('#AABBCC')).toBe('#aabbcc');
  });
  it('refuse une couleur contenant du CSS ou une URL', () => {
    for (const color of [
      'red',
      '#fff',
      '#fff; background:url(evil)',
      'url(javascript:alert(1))',
      null,
    ])
      expect(safeColor(color)).toBe(DEFAULT_COLOR);
  });
});
