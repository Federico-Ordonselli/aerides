import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AuthService } from '../auth/auth.service';
import { Language, LanguageService, SUPPORTED_LANGUAGES } from '../i18n/language.service';
import { NOTIFICATIONS_QUEUE, UserNotification } from '../realtime/realtime.models';
import { RealtimeService } from '../realtime/realtime.service';

/** Layout of every authenticated page: top bar + routed content. Also shows the user's live notifications. */
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
    MatMenuModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly authService = inject(AuthService);
  protected readonly languageService = inject(LanguageService);
  protected readonly languages = SUPPORTED_LANGUAGES;
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  constructor() {
    inject(RealtimeService)
      .watch<UserNotification>(NOTIFICATIONS_QUEUE)
      .pipe(takeUntilDestroyed())
      .subscribe((notification) => this.showNotification(notification));
  }

  protected changeLanguage(language: Language): void {
    this.languageService.use(language);
  }

  protected logout(): void {
    this.authService.logout();
  }

  private showNotification(notification: UserNotification): void {
    if (notification.type !== 'SIGNUP_PROMOTED') {
      return;
    }
    this.snackBar
      .open(
        this.translate.instant('realtime.promoted', { title: notification.eventTitle }),
        this.translate.instant('realtime.open'),
        { duration: 10000 },
      )
      .onAction()
      .subscribe(() => void this.router.navigate(['/groups', notification.groupId, 'events', notification.eventId]));
  }
}
