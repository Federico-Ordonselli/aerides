import { inject, Injectable, InjectionToken } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';

import { AuthResponse } from './auth.models';

const LOCK_NAME = 'aerides-refresh';
const CHANNEL_NAME = 'aerides-auth';

/** The browser's Web Locks, or null where they don't exist (old browsers, pages not served over HTTPS or localhost). */
export const WEB_LOCKS = new InjectionToken<LockManager | null>('WEB_LOCKS', {
  providedIn: 'root',
  factory: () => (typeof navigator !== 'undefined' && navigator.locks ? navigator.locks : null),
});

/** What a tab needs from a BroadcastChannel: send, and get the other tabs' messages. */
export interface AuthChannel {
  postMessage(message: AuthResponse): void;
  onmessage: ((event: MessageEvent<AuthResponse>) => void) | null;
}

export const AUTH_CHANNEL = new InjectionToken<AuthChannel | null>('AUTH_CHANNEL', {
  providedIn: 'root',
  factory: () =>
    typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL_NAME) : null,
});

/**
 * Lets only one tab at a time refresh the session.
 *
 * Every tab keeps its access token in memory, but they all share the refresh cookie. Two tabs that refresh at
 * the same moment send the same refresh token, and the backend sees the second one as a replayed token and
 * revokes every session. So the refresh runs under a Web Lock shared by the tabs of this origin:
 * - the tab holding the lock calls /refresh. The browser stores the rotated cookie, then the tab sends the new
 *   access token to the other tabs and releases the lock;
 * - a tab that waited for the lock reuses that token if it arrived while waiting. Otherwise (the message is
 *   late, or the other tab's refresh failed) it calls /refresh itself, with the new cookie: one rotation more,
 *   but never a replayed token.
 *
 * Without Web Locks the refresh runs directly, as before (one refresh per tab at a time, see AuthService).
 * A plain class so the tests can create several "tabs"; {@link CrossTabRefreshService} is the Angular one.
 */
export class CrossTabRefresh {
  /** Counts the other tabs' refreshes, to tell whether one happened while this tab was waiting. */
  private received = 0;
  private latest: AuthResponse | null = null;

  constructor(
    private readonly locks: LockManager | null,
    private readonly channel: AuthChannel | null,
  ) {
    if (channel) {
      channel.onmessage = (event) => {
        this.received++;
        this.latest = event.data;
      };
    }
  }

  run(refresh$: Observable<AuthResponse>): Observable<AuthResponse> {
    const locks = this.locks;
    if (!locks) {
      return refresh$;
    }
    return new Observable<AuthResponse>((subscriber) => {
      const receivedBefore = this.received;
      locks
        .request(LOCK_NAME, async () => {
          if (this.received > receivedBefore && this.latest) {
            return this.latest;
          }
          const response = await firstValueFrom(refresh$);
          // Before releasing the lock, so the next tab is likely to have the token when it gets it
          this.channel?.postMessage(response);
          return response;
        })
        .then(
          (response) => {
            subscriber.next(response);
            subscriber.complete();
          },
          (error: unknown) => subscriber.error(error),
        );
    });
  }
}

@Injectable({ providedIn: 'root' })
export class CrossTabRefreshService extends CrossTabRefresh {
  constructor() {
    super(inject(WEB_LOCKS), inject(AUTH_CHANNEL));
  }
}
