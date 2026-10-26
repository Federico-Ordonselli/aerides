import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../core/auth/auth.service';

/** Placeholder landing page: in Milestone 5 it becomes the dashboard. */
@Component({
  selector: 'app-home',
  imports: [TranslatePipe, MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-content>
        <h1 data-testid="welcome">{{ 'home.welcome' | translate: { name: authService.currentUser()?.displayName } }}</h1>
        <p>{{ 'home.intro' | translate }}</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class HomeComponent {
  protected readonly authService = inject(AuthService);
}
