export interface Master {
  id: number;
  name: string;
  email: string;
}
export interface ClinicRow {
  id: number;
  slug: string;
  name: string;
  city: string;
  address: string | null;
  color: string;
  createdAt: string;
  hasLogo: boolean;
  professionals: number;
  appointments30d: number;
}
export interface PractitionerRow {
  id: number;
  name: string;
  specialty: string;
  appointments30d: number;
  nextFreeSlot: string | null;
}
export interface ClinicDetail {
  clinic: ClinicRow;
  adminEmail: string | null;
  practitioners: PractitionerRow[];
}
export interface ClinicInput {
  name: string;
  slug: string;
  city: string;
  address: string;
  color: string;
  adminEmail: string;
}
export interface Created {
  clinic: ClinicRow;
  adminEmail: string;
  temporaryPassword: string;
}
export interface Stats {
  timezone: string;
  from: string;
  to: string;
  clinic: string;
  clinics: number;
  professionals: number;
  appointments: number;
  cancelled: number;
  perClinic: { slug: string; name: string; professionals: number; appointments: number }[];
  perWeek: { start: string; end: string; appointments: number; partial: boolean }[];
}
export interface AuditEntry {
  id: string;
  at: string;
  actor: string;
  role: string;
  action: string;
  target: string | null;
  platform: boolean;
}
export interface AuditPage {
  items: AuditEntry[];
  total: number;
  nextCursor: string | null;
}
export interface MasterEntry {
  id: number;
  at: string;
  actor: string;
  action: string;
  clinic: string | null;
  target: string | null;
}
export interface MasterPage {
  items: MasterEntry[];
  nextCursor: number | null;
}
export interface AppointmentRow {
  id: number;
  startsAt: string;
  patient: string;
  status: 'CONFIRMED' | 'PAST' | 'CANCELLED';
}
export interface PractitionerWeek {
  practitioner: { id: number; name: string; specialty: string };
  week: string;
  appointments: AppointmentRow[];
}
