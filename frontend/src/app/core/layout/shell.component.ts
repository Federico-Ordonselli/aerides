import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';

import { AuthService } from '../auth/auth.service';
import { Language, LanguageService, SUPPORTED_LANGUAGES } from '../i18n/language.service';

/** Layout of every authenticated page: top bar + routed content. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, TranslatePipe, ButtonModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly authService = inject(AuthService);
  protected readonly languageService = inject(LanguageService);
  protected readonly languages = SUPPORTED_LANGUAGES;

  protected changeLanguage(language: Language): void {
    this.languageService.use(language);
  }

  protected logout(): void {
    this.authService.logout();
  }
}
