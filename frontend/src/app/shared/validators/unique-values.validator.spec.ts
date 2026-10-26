import { FormArray, FormControl, FormGroup } from '@angular/forms';

import { uniqueValues } from './unique-values.validator';

function rows(...names: string[]): FormArray {
  return new FormArray(names.map((name) => new FormGroup({ roleName: new FormControl(name) })));
}

describe('uniqueValues', () => {
  const validator = uniqueValues('roleName');

  it('accepts distinct values', () => {
    expect(validator(rows('Tank', 'Healer', 'DPS'))).toBeNull();
  });

  it('reports duplicates ignoring case and spaces', () => {
    expect(validator(rows('Tank', 'Healer', ' tank '))).toEqual({ duplicateValues: ['tank'] });
  });

  it('ignores empty rows: they are reported by Validators.required', () => {
    expect(validator(rows('', '', 'Tank'))).toBeNull();
  });
});
