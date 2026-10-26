import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { distinctUntilChanged, EMPTY, filter, Observable, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { eventTopic, EventUpdate } from '../../core/realtime/realtime.models';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { confirmAction } from '../../shared/components/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { hasRole } from '../groups/group.models';
import { GroupsService } from '../groups/groups.service';
import { EventDetail, isEditable, Signup, Slot } from './event.models';
import { EventStatusComponent } from './event-status.component';
import { EventsService } from './events.service';
import { SlotCardComponent } from './slot-card.component';

/** An event with its slots: members sign up and withdraw, officers approve, publish, cancel and complete. */
@Component({
  selector: 'app-event-detail',
  imports: [
    DatePipe,
    RouterLink,
    TranslatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    EmptyStateComponent,
    EventStatusComponent,
    SlotCardComponent,
  ],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss',
})
export class EventDetailComponent {
  private readonly eventsService = inject(EventsService);
  private readonly groupsService = inject(GroupsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);
  protected readonly languageService = inject(LanguageService);

  readonly groupId = input.required({ transform: numberAttribute });
  readonly eventId = input.required({ transform: numberAttribute });

  protected readonly event = rxResource({
    params: () => ({ groupId: this.groupId(), eventId: this.eventId() }),
    stream: ({ params }) => this.eventsService.get(params.groupId, params.eventId),
  });
  protected readonly group = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.get(params),
  });

  protected readonly myUserId = computed(() => this.authService.currentUser()?.id ?? 0);
  protected readonly isOfficer = computed(() => this.group.hasValue() && hasRole(this.group.value().myRole, 'OFFICER'));
  protected readonly mySignup = computed(() => (this.event.hasValue() ? this.event.value().mySignup : null));
  /** The slot of the current user's sign-up, to show its role name. */
  protected readonly mySlot = computed(() => {
    const signup = this.mySignup();
    return signup && this.event.hasValue() ? this.event.value().slots.find((slot) => slot.id === signup.slotId) : undefined;
  });
  /** 1-based place in the waitlist, or 0 if the user is not waitlisted. */
  protected readonly myWaitlistPosition = computed(() => {
    const signup = this.mySignup();
    const slot = this.mySlot();
    return signup?.status === 'WAITLISTED' && slot ? slot.waitlisted.findIndex((s) => s.id === signup.id) + 1 : 0;
  });

  /**
   * Live updates: when anyone changes this event or its sign-ups, the server says so on the event's
   * topic and the page reloads it. Subscribed only once the event has loaded: if the user can't read
   * it, the server would refuse the subscription.
   */
  private readonly liveEventId = computed(() => (this.event.hasValue() ? this.eventId() : null));

  constructor() {
    const realtime = inject(RealtimeService);
    toObservable(this.liveEventId)
      .pipe(
        distinctUntilChanged(),
        switchMap((eventId) => (eventId === null ? EMPTY : realtime.watch<EventUpdate>(eventTopic(eventId)))),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.event.reload());
  }

  protected isEditable(event: EventDetail): boolean {
    return isEditable(event.status);
  }

  protected hasStarted(event: EventDetail): boolean {
    return new Date(event.startsAt).getTime() <= Date.now();
  }

  /** Same rule as the backend: only confirmed users are bound by the deadline. */
  protected deadlinePassed(event: EventDetail): boolean {
    return new Date(event.withdrawDeadline).getTime() < Date.now();
  }

  protected canSignUp(event: EventDetail): boolean {
    return event.status === 'PUBLISHED' && !this.hasStarted(event) && event.mySignup === null;
  }

  protected signUp(slot: Slot): void {
    this.run(this.eventsService.signUp(this.groupId(), this.eventId(), slot.id), (signup) =>
      this.notify(`events.detail.signedUp.${signup.status}`, { role: slot.roleName }),
    );
  }

  protected withdraw(): void {
    confirmAction(this.dialog, {
      title: this.translate.instant('events.detail.withdrawTitle'),
      message: this.translate.instant('events.detail.withdrawMessage'),
      confirmLabel: this.translate.instant('events.detail.withdraw'),
      destructive: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.eventsService.withdraw(this.groupId(), this.eventId())),
      )
      .subscribe({
        next: () => {
          this.notify('events.detail.withdrawn');
          this.event.reload();
        },
        error: (error: HttpErrorResponse) => this.failed(error),
      });
  }

  protected approve(signup: Signup): void {
    this.run(this.eventsService.approve(this.groupId(), this.eventId(), signup.id), (result) =>
      this.notify(`events.detail.approved.${result.status}`, { name: signup.displayName }),
    );
  }

  protected reject(signup: Signup): void {
    this.run(this.eventsService.reject(this.groupId(), this.eventId(), signup.id), () =>
      this.notify('events.detail.rejected', { name: signup.displayName }),
    );
  }

  protected publish(): void {
    this.run(this.eventsService.publish(this.groupId(), this.eventId()), () => this.notify('events.detail.published'));
  }

  protected complete(): void {
    this.run(this.eventsService.complete(this.groupId(), this.eventId()), () => this.notify('events.detail.completed'));
  }

  protected cancel(): void {
    confirmAction(this.dialog, {
      title: this.translate.instant('events.detail.cancelTitle'),
      message: this.translate.instant('events.detail.cancelMessage'),
      confirmLabel: this.translate.instant('events.detail.cancelEvent'),
      destructive: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.eventsService.cancel(this.groupId(), this.eventId())),
      )
      .subscribe({
        next: () => {
          this.notify('events.detail.cancelled');
          this.event.reload();
        },
        error: (error: HttpErrorResponse) => this.failed(error),
      });
  }

  /** Calls the API, then reloads the event in both cases: on error the page may be showing stale data. */
  private run<T>(request: Observable<T>, onSuccess: (result: T) => void): void {
    request.subscribe({
      next: (result) => {
        onSuccess(result);
        this.event.reload();
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  private failed(error: HttpErrorResponse): void {
    // 403 and 5xx already got a message from errorInterceptor
    if (error.status === 409 || error.status === 422 || error.status === 404) {
      const key = error.status === 422 ? 'events.detail.notAllowedNow' : 'events.detail.outdated';
      this.notify(key);
    }
    this.event.reload();
  }

  private notify(key: string, params?: Record<string, string>): void {
    this.snackBar.open(this.translate.instant(key, params), undefined, { duration: 4000 });
  }
}
