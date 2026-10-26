import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Friendly placeholder for empty lists. Put actions (buttons) inside the tag. */
@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="empty-state">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      <h2>{{ title() }}</h2>
      @if (message()) {
        <p>{{ message() }}</p>
      }
      <div class="actions"><ng-content /></div>
    </div>
  `,
  styles: `
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 3rem 1rem;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
    mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--mat-sys-primary);
    }
    h2 {
      margin: 0;
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-on-surface);
    }
    p {
      margin: 0;
      max-width: 32rem;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 0.5rem;
      margin-top: 1rem;
    }
  `,
})
export class EmptyStateComponent {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>();
}
