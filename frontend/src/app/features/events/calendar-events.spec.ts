import { toCalendarEvents } from './calendar-events';
import { EventSummary } from './event.models';

describe('toCalendarEvents', () => {
  it('computes the end from the duration and adds a class per status', () => {
    const event: EventSummary = {
      id: 7,
      title: 'Raid night',
      game: null,
      startsAt: '2027-03-18T20:00:00Z',
      durationMinutes: 150,
      status: 'PUBLISHED',
      requiresApproval: false,
      seriesId: null,
      totalCapacity: 10,
      confirmedCount: 4,
    };

    expect(toCalendarEvents([event])).toEqual([
      {
        id: '7',
        title: 'Raid night',
        start: '2027-03-18T20:00:00Z',
        end: '2027-03-18T22:30:00.000Z',
        className: 'event-published',
      },
    ]);
  });
});
