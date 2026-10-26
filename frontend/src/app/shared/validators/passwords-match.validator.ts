import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Form-group validator: fails with { passwordsMismatch: true } when the two controls have different values.
 * It is set on the group, not on a single control, because it needs to read two fields.
 */
export function passwordsMatch(passwordField: string, confirmField: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordField)?.value;
    const confirm = group.get(confirmField)?.value;
    if (!password || !confirm) {
      return null; // "required" is reported by the controls themselves
    }
    return password === confirm ? null : { passwordsMismatch: true };
  };
}
