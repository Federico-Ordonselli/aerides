import { FormControl } from '@angular/forms';

import { futureDate } from './future-date.validator';

describe('futureDate', () => {
  const now = new Date('2027-01-10T12:00:00Z');
  const validator = futureDate(() => now);

  it('accepts a date after now', () => {
    expect(validator(new FormControl(new Date('2027-01-10T12:00:01Z')))).toBeNull();
  });

  it('rejects now and the past', () => {
    expect(validator(new FormControl(new Date('2027-01-10T12:00:00Z')))).toEqual({ futureDate: true });
    expect(validator(new FormControl(new Date('2026-12-31T20:00:00Z')))).toEqual({ futureDate: true });
  });

  it('leaves an empty value to Validators.required', () => {
    expect(validator(new FormControl<Date | null>(null))).toBeNull();
  });
});
