import { clinicHeaders, clinicSnapshot, assertSameClinic } from './clinicRequest';

export async function fetchCalendar(id: number): Promise<Blob> {
  const snapshot = clinicSnapshot();
  const response = await fetch(`/api/appointments/${id}/calendar.ics`, {
    credentials: 'same-origin',
    headers: { Accept: 'text/calendar', ...clinicHeaders() },
  });
  assertSameClinic(snapshot);
  if (!response.ok || !response.headers.get('Content-Type')?.startsWith('text/calendar'))
    throw new Error('Le calendrier est indisponible.');
  const blob = await response.blob();
  assertSameClinic(snapshot);
  return blob;
}
