import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { Signup, Slot } from './event.models';

/**
 * One slot of an event: who is confirmed, who waits (in order) and who needs approval.
 * Presentational only: it emits the user's intent and the parent calls the API.
 */
@Component({
  selector: 'app-slot-card',
  imports: [TranslatePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  templateUrl: './slot-card.component.html',
  styleUrl: './slot-card.component.scss',
})
export class SlotCardComponent {
  readonly slot = input.required<Slot>();
  readonly myUserId = input.required<number>();
  /** The current user may sign up here: the event is open and they are not signed up yet. */
  readonly canSignUp = input(false);
  /** The current user is OFFICER or OWNER: they approve and reject pending sign-ups. */
  readonly canManage = input(false);

  readonly signUp = output<Slot>();
  readonly approve = output<Signup>();
  readonly reject = output<Signup>();

  protected readonly isFull = computed(() => this.slot().confirmed.length >= this.slot().capacity);
  protected readonly fillPercent = computed(() =>
    Math.min(100, (this.slot().confirmed.length / this.slot().capacity) * 100),
  );
}
