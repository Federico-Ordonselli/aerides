import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Fails with { maxUtf8Bytes: { max, actual } } when the value is longer than `max` bytes in UTF-8.
 * Mirrors the backend @MaxUtf8Bytes: BCrypt limits passwords to 72 bytes, and "é" is 2 bytes.
 */
export function maxUtf8Bytes(max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value === '') {
      return null;
    }
    const actual = new TextEncoder().encode(value).length;
    return actual <= max ? null : { maxUtf8Bytes: { max, actual } };
  };
}
