import { Component, computed, inject, input, output, ViewEncapsulation } from '@angular/core';
import { CalendarOptions, EventSourceFuncInfo, FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/angular/daygrid';
import themePlugin from '@fullcalendar/angular/themes/monarch';
import timeGridPlugin from '@fullcalendar/angular/timegrid';
// Imported from the core package: the @fullcalendar/angular/locales/* entry points are missing in 7.1.3
import itLocale from 'fullcalendar/locales/it';
import { firstValueFrom } from 'rxjs';

import { LanguageService } from '../../core/i18n/language.service';
import { toCalendarEvents } from './calendar-events';
import { EventsService } from './events.service';

/**
 * Month/week calendar of a group's events (FullCalendar, MIT packages only).
 * It asks the API only for the visible date range, every time the user moves to another month or week.
 * Loaded with @defer from the event list, so FullCalendar's code is downloaded only when the calendar is opened.
 */
@Component({
  selector: 'app-event-calendar',
  imports: [FullCalendarModule],
  template: `<full-calendar [options]="options()" />`,
  // The status classes are put on elements rendered by FullCalendar, outside this component's view
  encapsulation: ViewEncapsulation.None,
  styles: `
    app-event-calendar .event-draft {
      opacity: 0.6;
      border-style: dashed;
    }
    app-event-calendar .event-cancelled {
      text-decoration: line-through;
      opacity: 0.6;
    }
  `,
})
export class EventCalendarComponent {
  private readonly eventsService = inject(EventsService);
  private readonly languageService = inject(LanguageService);

  readonly groupId = input.required<number>();
  readonly eventSelected = output<number>();

  protected readonly options = computed<CalendarOptions>(() => {
    const groupId = this.groupId();
    return {
      plugins: [themePlugin, dayGridPlugin, timeGridPlugin],
      initialView: 'dayGridMonth',
      headerToolbar: { start: 'prev,next today', center: 'title', end: 'dayGridMonth,timeGridWeek' },
      locales: [itLocale],
      locale: this.languageService.current(),
      firstDay: 1,
      height: 'auto',
      // Called with the visible range whenever it changes; the returned promise feeds the calendar
      events: (range: EventSourceFuncInfo) =>
        firstValueFrom(
          this.eventsService.list(
            groupId,
            { from: range.start, to: range.end },
            { page: 0, size: 200, sort: 'startsAt,asc' },
          ),
        ).then((page) => toCalendarEvents(page.content)),
      eventClick: (click) => {
        click.jsEvent.preventDefault();
        this.eventSelected.emit(Number(click.event.id));
      },
    };
  });
}
