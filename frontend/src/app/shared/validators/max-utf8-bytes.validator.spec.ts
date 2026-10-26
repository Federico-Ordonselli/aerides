import { FormControl } from '@angular/forms';

import { maxUtf8Bytes } from './max-utf8-bytes.validator';

describe('maxUtf8Bytes', () => {
  it('accepts a value within the byte limit', () => {
    expect(maxUtf8Bytes(72)(new FormControl('a'.repeat(72)))).toBeNull();
  });

  it('counts bytes, not characters', () => {
    // 40 characters, 80 bytes
    expect(maxUtf8Bytes(72)(new FormControl('é'.repeat(40)))).toEqual({
      maxUtf8Bytes: { max: 72, actual: 80 },
    });
  });

  it('ignores empty values', () => {
    expect(maxUtf8Bytes(72)(new FormControl(''))).toBeNull();
  });
});
