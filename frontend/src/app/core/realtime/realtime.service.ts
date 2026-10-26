import { effect, inject, Injectable, InjectionToken } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { filter, map, Observable } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/** The STOMP client, behind a token so tests can replace it with a fake. */
export const RX_STOMP = new InjectionToken<RxStomp>('RX_STOMP', { factory: () => new RxStomp() });

/** Same message the backend puts in the ERROR frame when the access token is missing or expired. */
const AUTH_ERROR = 'Authentication required';

/**
 * One WebSocket (STOMP) connection for the whole app, open while the user is logged in.
 *
 * The access token goes in the CONNECT frame: browsers can't add an Authorization header to the
 * WebSocket handshake. It is read again before every (re)connection, so a reconnect uses the latest
 * token. If the server refuses an expired token, the session is refreshed and the next attempt succeeds.
 *
 * Subscriptions made with `watch()` survive reconnections: rx-stomp subscribes again automatically.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly stomp = inject(RX_STOMP);
  private readonly authService = inject(AuthService);

  constructor() {
    this.stomp.configure({
      brokerURL: webSocketUrl(),
      reconnectDelay: 5000,
      beforeConnect: (client) => {
        client.configure({ connectHeaders: { Authorization: `Bearer ${this.authService.getAccessToken()}` } });
      },
    });

    this.stomp.stompErrors$
      .pipe(filter((frame) => frame.headers['message'] === AUTH_ERROR))
      .subscribe(() => this.authService.refresh().subscribe({ error: () => undefined }));

    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.stomp.activate();
      } else {
        void this.stomp.deactivate();
      }
    });
  }

  /** Messages published on `destination`, parsed from JSON. Unsubscribing sends UNSUBSCRIBE to the server. */
  watch<T>(destination: string): Observable<T> {
    return this.stomp.watch(destination).pipe(map((message) => JSON.parse(message.body) as T));
  }
}

/** Same host as the page: nginx (and the dev-server proxy) forward /ws to the backend. */
function webSocketUrl(): string {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${location.host}/ws`;
}
