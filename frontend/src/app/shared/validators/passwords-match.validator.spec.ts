import { FormControl, FormGroup } from '@angular/forms';

import { passwordsMatch } from './passwords-match.validator';

describe('passwordsMatch', () => {
  function form(password: string, confirm: string): FormGroup {
    return new FormGroup(
      { password: new FormControl(password), confirm: new FormControl(confirm) },
      { validators: passwordsMatch('password', 'confirm') },
    );
  }

  it('is valid when the passwords are equal', () => {
    expect(form('supersecret', 'supersecret').errors).toBeNull();
  });

  it('reports a mismatch when the passwords differ', () => {
    expect(form('supersecret', 'different').errors).toEqual({ passwordsMismatch: true });
  });

  it('ignores empty fields, leaving them to the required validator', () => {
    expect(form('supersecret', '').errors).toBeNull();
  });
});
