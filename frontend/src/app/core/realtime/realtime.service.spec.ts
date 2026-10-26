import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { IFrame, IMessage } from '@stomp/stompjs';
import { of, Subject } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { RealtimeService, RX_STOMP } from './realtime.service';

describe('RealtimeService', () => {
  let stomp: {
    configure: jest.Mock;
    activate: jest.Mock;
    deactivate: jest.Mock;
    watch: jest.Mock;
    stompErrors$: Subject<IFrame>;
  };
  let authenticated: ReturnType<typeof signal<boolean>>;
  let auth: { isAuthenticated: () => boolean; getAccessToken: jest.Mock; refresh: jest.Mock };

  function create(): RealtimeService {
    TestBed.configureTestingModule({
      providers: [
        { provide: RX_STOMP, useValue: stomp },
        { provide: AuthService, useValue: auth },
      ],
    });
    const service = TestBed.inject(RealtimeService);
    TestBed.tick(); // runs the effect
    return service;
  }

  beforeEach(() => {
    stomp = {
      configure: jest.fn(),
      activate: jest.fn(),
      deactivate: jest.fn().mockResolvedValue(undefined),
      watch: jest.fn(),
      stompErrors$: new Subject<IFrame>(),
    };
    authenticated = signal(true);
    auth = {
      isAuthenticated: () => authenticated(),
      getAccessToken: jest.fn().mockReturnValue('token-1'),
      refresh: jest.fn().mockReturnValue(of({})),
    };
  });

  it('connects while the user is logged in and disconnects on logout', () => {
    create();
    expect(stomp.activate).toHaveBeenCalledTimes(1);

    authenticated.set(false);
    TestBed.tick();

    expect(stomp.deactivate).toHaveBeenCalledTimes(1);
  });

  it('puts the current access token in the CONNECT headers before every connection', () => {
    create();
    const config = stomp.configure.mock.calls[0][0] as RxStompConfig;
    const client = { configure: jest.fn() };

    auth.getAccessToken.mockReturnValue('token-2');
    void config.beforeConnect?.(client as unknown as RxStomp);

    expect(client.configure).toHaveBeenCalledWith({ connectHeaders: { Authorization: 'Bearer token-2' } });
  });

  it('refreshes the session when the server refuses the token, and only then', () => {
    create();

    stomp.stompErrors$.next({ headers: { message: 'Subscription not allowed' } } as unknown as IFrame);
    expect(auth.refresh).not.toHaveBeenCalled();

    stomp.stompErrors$.next({ headers: { message: 'Authentication required' } } as unknown as IFrame);
    expect(auth.refresh).toHaveBeenCalledTimes(1);
  });

  it('announces when the server closed the connection because access was revoked', () => {
    const service = create();
    const revoked: unknown[] = [];
    service.accessRevoked$.subscribe((frame) => revoked.push(frame));

    stomp.stompErrors$.next({ headers: { message: 'Subscription not allowed' } } as unknown as IFrame);
    stomp.stompErrors$.next({ headers: { message: 'Access revoked' } } as unknown as IFrame);

    expect(revoked).toHaveLength(1);
    expect(auth.refresh).not.toHaveBeenCalled();
  });

  it('parses the JSON body of the messages', () => {
    const messages = new Subject<IMessage>();
    stomp.watch.mockReturnValue(messages);
    const service = create();
    const received: unknown[] = [];

    service.watch('/topic/events/5').subscribe((message) => received.push(message));
    messages.next({ body: '{"eventId":5,"type":"SIGNUPS"}' } as IMessage);

    expect(stomp.watch).toHaveBeenCalledWith('/topic/events/5');
    expect(received).toEqual([{ eventId: 5, type: 'SIGNUPS' }]);
  });
});
