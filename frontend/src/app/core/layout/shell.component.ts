import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../auth/auth.service';
import { Language, LanguageService, SUPPORTED_LANGUAGES } from '../i18n/language.service';

/** Layout of every authenticated page: top bar + routed content. */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslatePipe,
    MatToolbarModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
  ],
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
