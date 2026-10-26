import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormArray, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTimepickerModule } from '@angular/material/timepicker';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { LanguageService } from '../../core/i18n/language.service';
import { futureDate } from '../../shared/validators/future-date.validator';
import { uniqueValues } from '../../shared/validators/unique-values.validator';
import { GamesService } from '../games/games.service';
import { EventDetail, EventRequest, isEditable, Slot } from './event.models';
import { EventsService } from './events.service';

export const MAX_SLOTS = 10;

/** Same limits as the backend request records. */
const LIMITS = {
  title: 120,
  description: 2000,
  roleName: 40,
  minDuration: 15,
  maxDuration: 1440,
  maxDeadlineHours: 168,
  maxCapacity: 100,
  minOccurrences: 2,
  maxOccurrences: 26,
};

/**
 * Create and edit form. Without an :eventId in the route it creates (optionally a weekly series),
 * with one it edits that single event.
 */
@Component({
  selector: 'app-event-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTimepickerModule,
    MatTooltipModule,
  ],
  providers: [
    provideNativeDateAdapter(),
    // Date and time pickers in the UI language (e.g. 26/10/2026 and 21:00 in Italian)
    { provide: MAT_DATE_LOCALE, useFactory: () => inject(LanguageService).current() },
  ],
  templateUrl: './event-form.component.html',
  styleUrl: './event-form.component.scss',
})
export class EventFormComponent {
  private readonly eventsService = inject(EventsService);
  private readonly gamesService = inject(GamesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly groupId = input.required({ transform: numberAttribute });
  /** Present only on the edit route. */
  readonly eventId = input<number | undefined, unknown>(undefined, {
    transform: (value: unknown) => (value == null ? undefined : numberAttribute(value)),
  });

  protected readonly limits = LIMITS;
  protected readonly maxSlots = MAX_SLOTS;
  protected readonly isEdit = computed(() => this.eventId() !== undefined);

  protected readonly games = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.gamesService.list(params),
  });
  // Loads only on the edit route: when params returns undefined, rxResource doesn't call the stream
  protected readonly existing = rxResource({
    params: () => {
      const eventId = this.eventId();
      return eventId === undefined ? undefined : { groupId: this.groupId(), eventId };
    },
    stream: ({ params }) => this.eventsService.get(params.groupId, params.eventId),
  });

  protected readonly form = this.formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(LIMITS.title)]],
    description: ['', Validators.maxLength(LIMITS.description)],
    gameId: [null as number | null],
    // One Date control, edited by both the date picker and the time picker
    startsAt: [defaultStart() as Date | null, [Validators.required, futureDate()]],
    durationMinutes: [
      120,
      [Validators.required, Validators.min(LIMITS.minDuration), Validators.max(LIMITS.maxDuration)],
    ],
    requiresApproval: [false],
    withdrawDeadlineHours: [0, [Validators.required, Validators.min(0), Validators.max(LIMITS.maxDeadlineHours)]],
    slots: this.formBuilder.array([this.slotGroup()], [Validators.required, uniqueValues('roleName')]),
    repeatWeekly: [false],
    weeklyOccurrences: [
      4,
      [Validators.required, Validators.min(LIMITS.minOccurrences), Validators.max(LIMITS.maxOccurrences)],
    ],
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<{ key: string; detail?: string } | null>(null);
  private version = 0;

  constructor() {
    // Edit mode: fill the form once the event arrives
    effect(() => {
      if (this.existing.hasValue()) {
        this.fillForm(this.existing.value());
      }
    });
  }

  protected get slots(): FormArray<FormGroup> {
    return this.form.controls.slots;
  }

  protected addSlot(): void {
    if (this.slots.length < MAX_SLOTS) {
      this.slots.push(this.slotGroup());
    }
  }

  protected removeSlot(index: number): void {
    if (this.slots.length > 1) {
      this.slots.removeAt(index);
    }
  }

  protected canEdit(event: EventDetail): boolean {
    return isEditable(event.status);
  }

  protected submit(): void {
    // The weekly series fields only matter when creating a series
    const repeat = !this.isEdit() && this.form.controls.repeatWeekly.value;
    if (repeat) {
      this.form.controls.weeklyOccurrences.enable();
    } else {
      this.form.controls.weeklyOccurrences.disable();
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.save(this.toRequest(), repeat).subscribe({
      next: (event) => {
        this.saving.set(false);
        // From ".../new" or ".../:id/edit" to ".../:id"
        void this.router.navigate(this.isEdit() ? ['..'] : ['..', event.id], { relativeTo: this.route });
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(toFormError(error));
      },
    });
  }

  private save(request: EventRequest, repeat: boolean): Observable<EventDetail> {
    const eventId = this.eventId();
    if (eventId !== undefined) {
      return this.eventsService.update(this.groupId(), eventId, { ...request, version: this.version });
    }
    return this.eventsService.create(this.groupId(), {
      ...request,
      weeklyOccurrences: repeat ? this.form.controls.weeklyOccurrences.value : null,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });
  }

  private toRequest(): EventRequest {
    const value = this.form.getRawValue();
    return {
      title: value.title.trim(),
      description: value.description.trim() || null,
      gameId: value.gameId,
      startsAt: (value.startsAt as Date).toISOString(),
      durationMinutes: value.durationMinutes,
      requiresApproval: value.requiresApproval,
      withdrawDeadlineHours: value.withdrawDeadlineHours,
      slots: this.slots.controls.map((slot) => ({
        id: slot.value.id ?? null,
        roleName: String(slot.value.roleName).trim(),
        capacity: Number(slot.value.capacity),
      })),
    };
  }

  private fillForm(event: EventDetail): void {
    this.version = event.version;
    this.slots.clear();
    event.slots.forEach((slot) => this.slots.push(this.slotGroup(slot)));
    this.form.patchValue({
      title: event.title,
      description: event.description ?? '',
      gameId: event.game?.id ?? null,
      startsAt: new Date(event.startsAt),
      durationMinutes: event.durationMinutes,
      requiresApproval: event.requiresApproval,
      withdrawDeadlineHours: event.withdrawDeadlineHours,
    });
  }

  private slotGroup(slot?: Slot): FormGroup {
    return this.formBuilder.group({
      // Kept so the backend knows which existing slot is which; null for a new slot
      id: [slot?.id ?? (null as number | null)],
      roleName: [slot?.roleName ?? '', [Validators.required, Validators.maxLength(LIMITS.roleName)]],
      capacity: [
        slot?.capacity ?? 1,
        [Validators.required, Validators.min(1), Validators.max(LIMITS.maxCapacity), Validators.pattern(/^\d+$/)],
      ],
    });
  }
}

/** Tomorrow at 21:00, a typical raid time. */
function defaultStart(): Date {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(21, 0, 0, 0);
  return date;
}

function toFormError(error: HttpErrorResponse): { key: string; detail?: string } {
  const detail: string | undefined = error.error?.detail;
  switch (error.status) {
    case 409:
      return { key: 'events.form.conflict', detail };
    case 400:
    case 422:
      return { key: 'events.form.invalid', detail };
    case 404:
      return { key: 'events.form.notFound' };
    default:
      return { key: 'errors.generic' };
  }
}
