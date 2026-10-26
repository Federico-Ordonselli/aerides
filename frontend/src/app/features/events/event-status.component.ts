import { Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { EventStatus } from './event.models';

/** Small colored label with the event status. */
@Component({
  selector: 'app-event-status',
  imports: [TranslatePipe],
  template: `<span class="status" [class]="'status status-' + status().toLowerCase()">{{
    'events.status.' + status() | translate
  }}</span>`,
  styles: `
    .status {
      display: inline-block;
      padding: 0.125rem 0.5rem;
      border-radius: var(--mat-sys-corner-full);
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
    }
    .status-draft {
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface-variant);
    }
    .status-published {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .status-cancelled {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .status-completed {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
  `,
})
export class EventStatusComponent {
  readonly status = input.required<EventStatus>();
}
