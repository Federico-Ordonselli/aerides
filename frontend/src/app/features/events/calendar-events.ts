import { EventSummary } from './event.models';

/** The subset of FullCalendar's EventInput we use. Kept separate so it can be unit-tested without the library. */
export interface CalendarEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  className: string;
}

/** Maps API events to calendar entries; the CSS class (see event-calendar.component) colors them by status. */
export function toCalendarEvents(events: EventSummary[]): CalendarEvent[] {
  return events.map((event) => ({
    id: String(event.id),
    title: event.title,
    start: event.startsAt,
    end: new Date(new Date(event.startsAt).getTime() + event.durationMinutes * 60_000).toISOString(),
    className: `event-${event.status.toLowerCase()}`,
  }));
}
