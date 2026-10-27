import { Game } from '../games/game.models';

/** Mirrors the backend EventStatus: DRAFT → PUBLISHED → COMPLETED, or CANCELLED. */
export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED';

export const EVENT_STATUSES: readonly EventStatus[] = ['DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'];

export type SignupStatus = 'CONFIRMED' | 'WAITLISTED' | 'PENDING' | 'REJECTED' | 'WITHDRAWN';

export interface EventSummary {
  id: number;
  title: string;
  game: Game | null;
  startsAt: string;
  durationMinutes: number;
  status: EventStatus;
  requiresApproval: boolean;
  seriesId: string | null;
  totalCapacity: number;
  confirmedCount: number;
}

export interface Signup {
  id: number;
  slotId: number;
  userId: number;
  displayName: string;
  avatarUrl: string | null;
  status: SignupStatus;
  position: number;
  createdAt: string;
}

/** A slot with its active sign-ups; each list is in FIFO order. */
export interface Slot {
  id: number;
  roleName: string;
  capacity: number;
  confirmed: Signup[];
  waitlisted: Signup[];
  pending: Signup[];
}

export interface EventDetail {
  id: number;
  title: string;
  description: string | null;
  game: Game | null;
  startsAt: string;
  durationMinutes: number;
  status: EventStatus;
  requiresApproval: boolean;
  withdrawDeadlineHours: number;
  withdrawDeadline: string;
  recurrenceRule: string | null;
  seriesId: string | null;
  slots: Slot[];
  /** The current user's active sign-up, or null. */
  mySignup: Signup | null;
  /** Sent back with an update: the backend rejects it (409) if the event changed in the meantime. */
  version: number;
}

export interface SlotRequest {
  /** null for a new slot. */
  id: number | null;
  roleName: string;
  capacity: number;
}

export interface EventRequest {
  title: string;
  description: string | null;
  gameId: number | null;
  startsAt: string;
  durationMinutes: number;
  requiresApproval: boolean;
  withdrawDeadlineHours: number;
  slots: SlotRequest[];
}

export interface CreateEventRequest extends EventRequest {
  /** null for a single event. */
  weeklyOccurrences: number | null;
  /** The browser's IANA zone, so a weekly series keeps its local time across daylight saving changes. */
  timeZone: string;
}

export interface UpdateEventRequest extends EventRequest {
  version: number;
}

/** Optional filters of the event list; `to` is exclusive. */
export interface EventFilter {
  from?: Date | null;
  to?: Date | null;
  status?: EventStatus | null;
  gameId?: number | null;
}


/** Statuses in which an event can still be edited. */
export function isEditable(status: EventStatus): boolean {
  return status === 'DRAFT' || status === 'PUBLISHED';
}
