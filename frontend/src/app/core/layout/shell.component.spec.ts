import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { UserNotification } from '../realtime/realtime.models';
import { RealtimeService } from '../realtime/realtime.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let notifications: Subject<UserNotification>;
  let snackBar: { open: jest.Mock };
  let snackBarAction: Subject<void>;

  beforeEach(async () => {
    notifications = new Subject<UserNotification>();
    snackBarAction = new Subject<void>();
    snackBar = { open: jest.fn(() => ({ onAction: () => snackBarAction })) };
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: AuthService, useValue: { currentUser: signal(null), logout: jest.fn() } },
        { provide: RealtimeService, useValue: { watch: jest.fn(() => notifications) } },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    await fixture.whenStable();
  });

  it('listens to the personal queue and shows a toast when the user is promoted', () => {
    expect(TestBed.inject(RealtimeService).watch).toHaveBeenCalledWith('/user/queue/notifications');

    notifications.next({ type: 'SIGNUP_PROMOTED', groupId: 1, eventId: 5, eventTitle: 'Raid night' });

    // No translations loaded in tests: the pipe returns the key
    expect(snackBar.open).toHaveBeenCalledWith('realtime.promoted', 'realtime.open', { duration: 10000 });
  });

  it('opens the event from the toast action', () => {
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    notifications.next({ type: 'SIGNUP_PROMOTED', groupId: 1, eventId: 5, eventTitle: 'Raid night' });

    snackBarAction.next();

    expect(navigate).toHaveBeenCalledWith(['/groups', 1, 'events', 5]);
  });
});
