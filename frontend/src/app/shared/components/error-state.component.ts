import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TranslatePipe } from '@ngx-translate/core';

import { EmptyStateComponent } from './empty-state.component';

/**
 * Shown in place of content that failed to load, with a retry button.
 * Usage: `<app-error-state (retry)="resource.reload()" />`.
 */
@Component({
  selector: 'app-error-state',
  imports: [TranslatePipe, MatButtonModule, EmptyStateComponent],
  template: `
    <app-empty-state icon="cloud_off" [title]="titleKey() | translate" [message]="'errors.loadFailed' | translate">
      <button mat-stroked-button type="button" (click)="retry.emit()" data-testid="retry">
        {{ 'common.retry' | translate }}
      </button>
    </app-empty-state>
  `,
})
export class ErrorStateComponent {
  readonly titleKey = input('errors.generic');
  readonly retry = output<void>();
}
