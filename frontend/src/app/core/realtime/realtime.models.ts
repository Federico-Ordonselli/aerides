/** Sent on /topic/events/{id}: something changed, reload the event. */
export interface EventUpdate {
  eventId: number;
  type: 'DETAILS' | 'STATUS' | 'SIGNUPS';
}

/** Sent on the user's personal queue, shown as a toast. */
export interface UserNotification {
  type: 'SIGNUP_PROMOTED';
  groupId: number;
  eventId: number;
  eventTitle: string;
}

export const NOTIFICATIONS_QUEUE = '/user/queue/notifications';

export function eventTopic(eventId: number): string {
  return `/topic/events/${eventId}`;
}
