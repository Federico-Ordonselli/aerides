/** Mirrors the backend AuditAction. */
export const AUDIT_ACTIONS = [
  'GROUP_CREATED',
  'GROUP_UPDATED',
  'GROUP_DELETED',
  'INVITE_CODE_REGENERATED',
  'DISCORD_WEBHOOK_UPDATED',
  'MEMBER_JOINED',
  'MEMBER_ROLE_CHANGED',
  'MEMBER_REMOVED',
  'MEMBER_LEFT',
  'GAME_ADDED',
  'GAME_REMOVED',
  'EVENT_CREATED',
  'EVENT_UPDATED',
  'EVENT_PUBLISHED',
  'EVENT_CANCELLED',
  'EVENT_COMPLETED',
  'SIGNUP_APPROVED',
  'SIGNUP_REJECTED',
] as const;
export type AuditAction = (typeof AUDIT_ACTIONS)[number];

export type AuditEntityType = 'GROUP' | 'MEMBERSHIP' | 'GAME' | 'EVENT' | 'SIGNUP';

export interface AuditEntry {
  id: number;
  action: AuditAction;
  entityType: AuditEntityType;
  entityId: number | null;
  actorId: number | null;
  /** null when the actor's account was deleted. */
  actorName: string | null;
  details: Record<string, unknown>;
  createdAt: string;
}

/** One "key: value" pair of an entry's details, ready to display. */
export interface DetailItem {
  key: string;
  value: string;
}

/**
 * PostgreSQL's JSONB does not keep the order of the keys, so the display order is decided here:
 * who and what first, then the change. Keys not listed keep their order, after these.
 */
const KEY_ORDER = ['userId', 'eventId', 'name', 'title', 'startsAt', 'from', 'to', 'status'];

/** Technical ids that mean nothing to a reader (a series is a UUID). */
const HIDDEN_KEYS = new Set(['seriesId']);

/**
 * Turns the free-form JSON details into readable pairs:
 * user ids become member names when known, dates are formatted in the UI language,
 * {from, to} objects become "from → to", lists are joined.
 */
export function describeDetails(
  details: Record<string, unknown>,
  memberNames: ReadonlyMap<number, string>,
  locale: string,
): DetailItem[] {
  return Object.entries(details)
    .filter(([key]) => !HIDDEN_KEYS.has(key))
    .sort(([a], [b]) => rank(a) - rank(b))
    .map(([key, value]) => ({ key, value: describeValue(key, value, memberNames, locale) }));
}

function rank(key: string): number {
  const index = KEY_ORDER.indexOf(key);
  return index === -1 ? KEY_ORDER.length : index;
}

function describeValue(key: string, value: unknown, memberNames: ReadonlyMap<number, string>, locale: string): string {
  if (key === 'userId' && typeof value === 'number') {
    return memberNames.get(value) ?? `#${value}`;
  }
  if (key === 'startsAt' && typeof value === 'string') {
    return new Date(value).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
  }
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (isChange(value)) {
    return `${String(value.from)} → ${String(value.to)}`;
  }
  if (value !== null && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function isChange(value: unknown): value is { from: unknown; to: unknown } {
  return value !== null && typeof value === 'object' && 'from' in value && 'to' in value;
}
