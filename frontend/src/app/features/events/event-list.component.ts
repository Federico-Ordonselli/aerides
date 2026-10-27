import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageRequest } from '../../shared/models/page.models';
import { GamesService } from '../games/games.service';
import { hasRole } from '../groups/group.models';
import { GroupsService } from '../groups/groups.service';
import { EventCalendarComponent } from './event-calendar.component';
import { EVENT_STATUSES, EventFilter, EventStatus } from './event.models';
import { EventStatusComponent } from './event-status.component';
import { EventsService } from './events.service';

const DEFAULT_SORT = 'startsAt,asc';

/** Events of a group, as a paginated, filterable table or as a calendar. */
@Component({
  selector: 'app-event-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    EmptyStateComponent,
    ErrorStateComponent,
    EventCalendarComponent,
    EventStatusComponent,
  ],
  providers: [
    provideNativeDateAdapter(),
    // Date and time pickers in the UI language (e.g. 26/10/2026 and 21:00 in Italian)
    { provide: MAT_DATE_LOCALE, useFactory: () => inject(LanguageService).current() },
  ],
  templateUrl: './event-list.component.html',
  styleUrl: './event-list.component.scss',
})
export class EventListComponent {
  private readonly eventsService = inject(EventsService);
  private readonly groupsService = inject(GroupsService);
  private readonly gamesService = inject(GamesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly languageService = inject(LanguageService);

  readonly groupId = input.required({ transform: numberAttribute });

  protected readonly statuses = EVENT_STATUSES;
  protected readonly columns = ['startsAt', 'title', 'status', 'seats'];
  protected readonly view = signal<'list' | 'calendar'>('list');

  protected readonly group = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.get(params),
  });
  protected readonly games = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.gamesService.list(params),
  });
  protected readonly isOfficer = computed(() => this.group.hasValue() && hasRole(this.group.value().myRole, 'OFFICER'));

  // By default only upcoming events: the date filter starts from today
  protected readonly filterForm = inject(NonNullableFormBuilder).group({
    status: [null as EventStatus | null],
    gameId: [null as number | null],
    from: [startOfToday() as Date | null],
    to: [null as Date | null],
  });
  private readonly filterValue = toSignal(this.filterForm.valueChanges, { initialValue: this.filterForm.getRawValue() });
  protected readonly pageRequest = signal<PageRequest>({ page: 0, size: 10, sort: DEFAULT_SORT });

  // Reloads whenever the group, a filter, the page or the sort changes
  protected readonly events = rxResource({
    params: () => ({ groupId: this.groupId(), filter: toApiFilter(this.filterValue()), page: this.pageRequest() }),
    stream: ({ params }) => this.eventsService.list(params.groupId, params.filter, params.page),
  });

  constructor() {
    // A different filter starts again from the first page
    this.filterForm.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageRequest.update((request) => ({ ...request, page: 0 })));
  }

  protected pageChanged(event: PageEvent): void {
    this.pageRequest.update((request) => ({ ...request, page: event.pageIndex, size: event.pageSize }));
  }

  protected sortChanged(sort: Sort): void {
    const value = sort.direction ? `${sort.active},${sort.direction}` : DEFAULT_SORT;
    this.pageRequest.update((request) => ({ ...request, page: 0, sort: value }));
  }

  protected openEvent(eventId: number): void {
    void this.router.navigate([eventId], { relativeTo: this.route });
  }

  protected clearDates(): void {
    this.filterForm.patchValue({ from: null, to: null });
  }
}

/** The date picker gives the start of the chosen day; `to` is exclusive, so it moves to the next day. */
function toApiFilter(value: Partial<{ status: EventStatus | null; gameId: number | null; from: Date | null; to: Date | null }>): EventFilter {
  const to = value.to ? new Date(value.to.getFullYear(), value.to.getMonth(), value.to.getDate() + 1) : null;
  return { status: value.status ?? null, gameId: value.gameId ?? null, from: value.from ?? null, to };
}

function startOfToday(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}
