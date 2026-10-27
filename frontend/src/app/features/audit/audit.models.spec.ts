import { describeDetails } from './audit.models';

describe('describeDetails', () => {
  const members = new Map([[7, 'Marco']]);

  it('shows member names instead of user ids, in a fixed order whatever the JSON order', () => {
    expect(describeDetails({ to: 'OFFICER', from: 'MEMBER', userId: 7 }, members, 'en')).toEqual([
      { key: 'userId', value: 'Marco' },
      { key: 'from', value: 'MEMBER' },
      { key: 'to', value: 'OFFICER' },
    ]);
    expect(describeDetails({ userId: 99 }, members, 'en')).toEqual([{ key: 'userId', value: '#99' }]);
  });

  it('formats changes, lists and flags', () => {
    expect(
      describeDetails({ name: { from: 'Old', to: 'New' }, changed: ['title', 'slots'], configured: false }, members),
    ).toEqual([
      { key: 'name', value: 'Old → New' },
      { key: 'changed', value: 'title, slots' },
      { key: 'configured', value: 'false' },
    ]);
  });

  it('formats dates in the UI language and hides series ids', () => {
    const details = { seriesId: 'aa687522-5445-4dbd-9c32-4a8a80d05bba', startsAt: '2026-11-19T20:00:00Z' };

    const [item, ...others] = describeDetails(details, members, 'it');

    expect(others).toEqual([]);
    expect(item.key).toBe('startsAt');
    expect(item.value).toContain('19 nov 2026');
  });
});
