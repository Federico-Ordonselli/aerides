import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { Observable, of, Subject, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { EventUpdate } from '../../core/realtime/realtime.models';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { MembershipRole } from '../groups/group.models';
import { GroupsService } from '../groups/groups.service';
import { EventDetailComponent } from './event-detail.component';
import { EventDetail, Signup } from './event.models';
import { EventsService } from './events.service';

const ME = 12;

function signup(id: number, userId: number, status: Signup['status']): Signup {
  return { id, slotId: 1, userId, displayName: `User ${userId}`, avatarUrl: null, status, position: id, createdAt: '' };
}

function event(overrides: Partial<EventDetail>): EventDetail {
  return {
    id: 5,
    title: 'Raid night',
    description: null,
    game: null,
    startsAt: '2099-03-18T20:00:00Z',
    durationMinutes: 180,
    status: 'PUBLISHED',
    requiresApproval: false,
    withdrawDeadlineHours: 0,
    withdrawDeadline: '2099-03-18T20:00:00Z',
    recurrenceRule: null,
    seriesId: null,
    slots: [
      {
        id: 1,
        roleName: 'Tank',
        capacity: 1,
        confirmed: [signup(1, 10, 'CONFIRMED')],
        waitlisted: [signup(2, 11, 'WAITLISTED'), signup(3, ME, 'WAITLISTED')],
        pending: [],
      },
    ],
    mySignup: null,
    version: 0,
    ...overrides,
  };
}

describe('EventDetailComponent', () => {
  let eventsService: { get: jest.Mock };
  let realtime: { watch: jest.Mock; accessRevoked$: Subject<unknown> };
  let updates: Subject<EventUpdate>;

  async function render(detail: EventDetail, myRole: MembershipRole): Promise<HTMLElement> {
    eventsService = { get: jest.fn((): Observable<EventDetail> => of(detail)) };
    updates = new Subject<EventUpdate>();
    realtime = { watch: jest.fn(() => updates), accessRevoked$: new Subject<unknown>() };
    TestBed.configureTestingModule({
      imports: [EventDetailComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: EventsService, useValue: eventsService },
        { provide: RealtimeService, useValue: realtime },
        { provide: GroupsService, useValue: { get: () => of({ id: 1, myRole }) } },
        { provide: AuthService, useValue: { currentUser: signal({ id: ME }) } },
      ],
    });
    const fixture = TestBed.createComponent(EventDetailComponent);
    fixture.componentRef.setInput('groupId', 1);
    fixture.componentRef.setInput('eventId', 5);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the current user their place in the waitlist and lets them withdraw', async () => {
    const element = await render(event({ mySignup: signup(3, ME, 'WAITLISTED') }), 'MEMBER');

    const banner = element.querySelector('[data-testid="my-signup"]') as HTMLElement;
    expect(banner.textContent).toContain('events.detail.youAreWaitlisted');
    expect(element.querySelector('[data-testid="withdraw"]')).not.toBeNull();
    // Already signed up: no sign-up button on the slots
    expect(element.querySelector('[data-testid="sign-up"]')).toBeNull();
  });

  it('hides the withdraw button from confirmed users after the deadline', async () => {
    const element = await render(
      event({ mySignup: signup(1, ME, 'CONFIRMED'), withdrawDeadline: '2020-01-01T00:00:00Z' }),
      'MEMBER',
    );

    expect(element.querySelector('[data-testid="withdraw"]')).toBeNull();
    expect(element.querySelector('[data-testid="my-signup"]')?.textContent).toContain('events.detail.deadlinePassed');
  });

  it('offers sign-up to members who are not signed up', async () => {
    const element = await render(event({}), 'MEMBER');

    expect(element.querySelector('[data-testid="sign-up"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="edit-event"]')).toBeNull();
  });

  it('shows the management actions only to officers', async () => {
    const element = await render(event({ status: 'DRAFT' }), 'OFFICER');

    expect(element.querySelector('[data-testid="edit-event"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="publish-event"]')).not.toBeNull();
    // A draft is not open for sign-ups
    expect(element.querySelector('[data-testid="sign-up"]')).toBeNull();
  });

  it('listens to the event topic and reloads the event when the server announces a change', async () => {
    await render(event({}), 'MEMBER');
    expect(realtime.watch).toHaveBeenCalledWith('/topic/events/5');
    expect(eventsService.get).toHaveBeenCalledTimes(1);

    updates.next({ eventId: 5, type: 'SIGNUPS' });
    await TestBed.inject(ApplicationRef).whenStable();

    expect(eventsService.get).toHaveBeenCalledTimes(2);
  });

  it('stops watching the event when access to the group is revoked', async () => {
    await render(event({}), 'MEMBER');
    expect(updates.observed).toBe(true);
    // The user was removed from the group: the event is no longer readable
    eventsService.get.mockReturnValue(throwError(() => new Error('404')));

    realtime.accessRevoked$.next({});
    await TestBed.inject(ApplicationRef).whenStable();

    expect(eventsService.get).toHaveBeenCalledTimes(2);
    expect(updates.observed).toBe(false);
  });

  it('does not subscribe when the event could not be loaded', async () => {
    eventsService = { get: jest.fn() };
    TestBed.configureTestingModule({
      imports: [EventDetailComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: EventsService, useValue: { get: () => throwError(() => new Error('404')) } },
        { provide: RealtimeService, useValue: (realtime = { watch: jest.fn(), accessRevoked$: new Subject() }) },
        { provide: GroupsService, useValue: { get: () => of({ id: 1, myRole: 'MEMBER' }) } },
        { provide: AuthService, useValue: { currentUser: signal({ id: ME }) } },
      ],
    });
    const fixture = TestBed.createComponent(EventDetailComponent);
    fixture.componentRef.setInput('groupId', 1);
    fixture.componentRef.setInput('eventId', 5);
    await fixture.whenStable();

    expect(realtime.watch).not.toHaveBeenCalled();
  });
});
