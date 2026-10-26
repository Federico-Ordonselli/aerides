import { AbstractControl, FormArray, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * FormArray validator: fails with { duplicateValues: ['tank'] } when two rows have the same `field`,
 * ignoring case and surrounding spaces (same rule as the backend for slot role names).
 * It is set on the array, not on a row, because it needs to see all the rows.
 */
export function uniqueValues(field: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!(control instanceof FormArray)) {
      return null;
    }
    const seen = new Set<string>();
    const duplicates = new Set<string>();
    for (const row of control.controls) {
      const value = String(row.get(field)?.value ?? '').trim().toLowerCase();
      if (!value) {
        continue;
      }
      if (seen.has(value)) {
        duplicates.add(value);
      }
      seen.add(value);
    }
    return duplicates.size > 0 ? { duplicateValues: [...duplicates] } : null;
  };
}
