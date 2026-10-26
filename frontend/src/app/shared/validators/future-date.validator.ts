import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Fails with { futureDate: true } when the control's Date is not in the future.
 * `now` is a parameter so tests can pass a fixed clock; the backend checks the same rule with @Future.
 */
export function futureDate(now: () => Date = () => new Date()): ValidatorFn {
  return (control: AbstractControl<Date | null>): ValidationErrors | null => {
    const value = control.value;
    if (!value) {
      return null; // "required" is reported by Validators.required
    }
    return value.getTime() > now().getTime() ? null : { futureDate: true };
  };
}
