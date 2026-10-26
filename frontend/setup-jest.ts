// Must stay the first import: it loads the Angular compiler that the other Angular imports rely on
import { setupZonelessTestEnv } from 'jest-preset-angular/setup-env/zoneless';

import { registerLocaleData } from '@angular/common';
import localeIt from '@angular/common/locales/it';

setupZonelessTestEnv();

// Same locales as the app (see app.config.ts), so pipes like DatePipe work with "it" in tests
registerLocaleData(localeIt);
