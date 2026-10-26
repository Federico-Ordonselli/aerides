import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { MatIconRegistry } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { errorInterceptor } from './core/http/error.interceptor';
import { LanguageService } from './core/i18n/language.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideTranslateService({
      // useHttpBackend: load translation files without our interceptors. Otherwise errorInterceptor
      // (which needs TranslateService) would be required while TranslateService is still being created.
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json', useHttpBackend: true }),
      fallbackLang: 'it',
    }),
    // Runs before the first navigation, so guards already know whether the user is logged in
    provideAppInitializer(() => {
      // index.html loads the "Material Symbols Outlined" icon font
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
      inject(LanguageService).init();
      return firstValueFrom(inject(AuthService).restoreSession());
    }),
  ],
};
