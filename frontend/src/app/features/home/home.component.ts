import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';

import { AuthService } from '../../core/auth/auth.service';

/** Placeholder landing page: in Milestone 5 it becomes the dashboard. */
@Component({
  selector: 'app-home',
  imports: [TranslatePipe, CardModule],
  template: `
    <p-card>
      <h1 data-testid="welcome">{{ 'home.welcome' | translate: { name: authService.currentUser()?.displayName } }}</h1>
      <p>{{ 'home.intro' | translate }}</p>
    </p-card>
  `,
})
export class HomeComponent {
  protected readonly authService = inject(AuthService);
}
