import { getActivePinia } from 'pinia';
import { useClinic } from './stores/clinic';

export class ClinicChangedError extends Error {
  constructor() {
    super('Le cabinet a changé pendant la requête.');
  }
}
export function clinicSnapshot() {
  if (!getActivePinia()) return null;
  const clinic = useClinic();
  return { slug: clinic.slug, revision: clinic.revision };
}
export function assertSameClinic(snapshot: ReturnType<typeof clinicSnapshot>) {
  const current = clinicSnapshot();
  if (snapshot && (snapshot.slug !== current?.slug || snapshot.revision !== current?.revision))
    throw new ClinicChangedError();
}
export function clinicHeaders(): Record<string, string> {
  const snapshot = clinicSnapshot();
  return snapshot ? { 'X-Clinic': snapshot.slug } : {};
}
