import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';

import { GamesService } from '../games/games.service';
import { EventFormComponent } from './event-form.component';
import { CreateEventRequest, EventDetail, UpdateEventRequest } from './event.models';
import { EventsService } from './events.service';

const EXISTING: EventDetail = {
  id: 5,
  title: 'Raid night',
  description: null,
  game: null,
  startsAt: '2099-03-18T20:00:00Z',
  durationMinutes: 180,
  status: 'PUBLISHED',
  requiresApproval: false,
  withdrawDeadlineHours: 24,
  withdrawDeadline: '2099-03-17T20:00:00Z',
  recurrenceRule: null,
  seriesId: null,
  slots: [{ id: 11, roleName: 'Tank', capacity: 2, confirmed: [], waitlisted: [], pending: [] }],
  mySignup: null,
  version: 3,
};

describe('EventFormComponent', () => {
  const create = jest.fn();
  const update = jest.fn();
  let element: HTMLElement;

  async function render(eventId?: number): Promise<void> {
    create.mockReset().mockReturnValue(of(EXISTING));
    update.mockReset().mockReturnValue(of(EXISTING));
    TestBed.configureTestingModule({
      imports: [EventFormComponent],
      providers: [
        // Any URL: after saving, the form navigates to the event page
        provideRouter([{ path: '**', children: [] }]),
        provideTranslateService(),
        { provide: EventsService, useValue: { create, update, get: () => of(EXISTING) } },
        { provide: GamesService, useValue: { list: () => of([]) } },
      ],
    });
    const fixture = TestBed.createComponent(EventFormComponent);
    fixture.componentRef.setInput('groupId', 1);
    if (eventId !== undefined) {
      fixture.componentRef.setInput('eventId', eventId);
    }
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function type(testId: string, value: string, index = 0): void {
    const input = element.querySelectorAll(`[data-testid="${testId}"]`)[index] as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    (element.querySelector('[data-testid="save-event"]') as HTMLButtonElement).click();
    TestBed.tick();
  }

  function addSlot(): void {
    (element.querySelector('[data-testid="add-slot"]') as HTMLButtonElement).click();
    TestBed.tick();
  }

  it('creates a single event with its slots and the browser time zone', async () => {
    await render();
    type('title', '  Raid night ');
    type('role-name', 'Tank');
    type('capacity', '2');
    addSlot();
    type('role-name', 'Healer', 1);

    submit();

    expect(create).toHaveBeenCalledTimes(1);
    const [groupId, request] = create.mock.calls[0] as [number, CreateEventRequest];
    expect(groupId).toBe(1);
    expect(request.title).toBe('Raid night');
    expect(request.slots).toEqual([
      { id: null, roleName: 'Tank', capacity: 2 },
      { id: null, roleName: 'Healer', capacity: 1 },
    ]);
    expect(request.weeklyOccurrences).toBeNull();
    expect(request.timeZone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });

  it('does not submit when two slots have the same role', async () => {
    await render();
    type('title', 'Raid night');
    type('role-name', 'Tank');
    addSlot();
    type('role-name', ' tank', 1);

    submit();

    expect(create).not.toHaveBeenCalled();
    expect(element.querySelector('[data-testid="duplicate-roles"]')).not.toBeNull();
  });

  it('does not submit a slot without seats', async () => {
    await render();
    type('title', 'Raid night');
    type('role-name', 'Tank');
    type('capacity', '0');

    submit();

    expect(create).not.toHaveBeenCalled();
    expect(element.querySelector('[data-testid="capacity-error"]')).not.toBeNull();
  });

  it('edits an existing event, keeping slot ids and sending the loaded version', async () => {
    await render(5);
    TestBed.tick();
    expect((element.querySelector('[data-testid="title"]') as HTMLInputElement).value).toBe('Raid night');
    type('capacity', '4');

    submit();

    expect(update).toHaveBeenCalledTimes(1);
    const [, eventId, request] = update.mock.calls[0] as [number, number, UpdateEventRequest];
    expect(eventId).toBe(5);
    expect(request.version).toBe(3);
    expect(request.slots).toEqual([{ id: 11, roleName: 'Tank', capacity: 4 }]);
    expect(request.withdrawDeadlineHours).toBe(24);
  });
});
