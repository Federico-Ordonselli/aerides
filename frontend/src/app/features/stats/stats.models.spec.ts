import { monthLabel, monthlyChartData } from './stats-chart';
import { GroupStats, hasNoEvents, periodStart } from './stats.models';

describe('stats helpers', () => {
  it('starts a period on the first day of the month, counting the current month', () => {
    const today = new Date(2027, 2, 15); // 15 March 2027

    expect(periodStart(3, today)).toEqual(new Date(2027, 0, 1));
    expect(periodStart(12, today)).toEqual(new Date(2026, 3, 1));
  });

  it('builds one bar per month for completed and cancelled events', () => {
    const data = monthlyChartData(
      [
        { month: '2026-09', completed: 2, cancelled: 0 },
        { month: '2026-10', completed: 0, cancelled: 1 },
      ],
      'en',
      { completed: 'Completed', cancelled: 'Cancelled' },
      { completed: 'violet', cancelled: 'red' },
    );

    expect(data.labels).toEqual(['Sep 26', 'Oct 26']);
    expect(data.datasets.map((dataset) => [dataset.label, dataset.data])).toEqual([
      ['Completed', [2, 0]],
      ['Cancelled', [0, 1]],
    ]);
  });

  it('writes month names in the UI language', () => {
    expect(monthLabel('2026-09', 'it')).toBe('set 26');
  });

  it('detects a period without finished events', () => {
    const stats = (completed: number, cancelled: number): GroupStats => ({
      from: '',
      to: '',
      zone: 'UTC',
      attendance: [],
      eventsPerMonth: [{ month: '2026-09', completed, cancelled }],
      unfilledRoles: [],
    });

    expect(hasNoEvents(stats(0, 0))).toBe(true);
    expect(hasNoEvents(stats(0, 1))).toBe(false);
  });
});
