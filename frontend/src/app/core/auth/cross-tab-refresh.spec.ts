import { Observable, Subject } from 'rxjs';

import { AuthResponse } from './auth.models';
import { authResponse } from './auth.testing';
import { AuthChannel, CrossTabRefresh } from './cross-tab-refresh';

/** One exclusive lock for the whole origin, granted in request order like the browser's. */
class FakeLocks {
  private queue: Promise<unknown> = Promise.resolve();

  request(_name: string, callback: () => Promise<AuthResponse>): Promise<AuthResponse> {
    const result = this.queue.then(callback);
    this.queue = result.catch(() => undefined);
    return result;
  }
}

/** A BroadcastChannel between "tabs": a message reaches every other tab, not the sender. */
class FakeBus {
  private readonly channels: AuthChannel[] = [];
  /** When false, messages are kept back: they are late, as BroadcastChannel is allowed to be. */
  deliver = true;

  channel(): AuthChannel {
    const channel: AuthChannel = {
      onmessage: null,
      postMessage: (message) => {
        if (!this.deliver) {
          return;
        }
        for (const other of this.channels.filter((c) => c !== channel)) {
          other.onmessage?.({ data: message } as MessageEvent<AuthResponse>);
        }
      },
    };
    this.channels.push(channel);
    return channel;
  }
}

/** A /refresh call that answers only when the test says so, and counts how often it was sent. */
class FakeRefresh {
  calls = 0;
  private pending: Subject<AuthResponse> | null = null;

  readonly request$ = new Observable<AuthResponse>((subscriber) => {
    this.calls++;
    this.pending = new Subject<AuthResponse>();
    return this.pending.subscribe(subscriber);
  });

  succeed(token: string): void {
    this.pending?.next(authResponse(token));
    this.pending?.complete();
  }

  fail(): void {
    this.pending?.error(new Error('401'));
  }
}

/** Lets the lock's promise callbacks run. */
const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('CrossTabRefresh', () => {
  let locks: FakeLocks;
  let bus: FakeBus;

  beforeEach(() => {
    locks = new FakeLocks();
    bus = new FakeBus();
  });

  function tab(): CrossTabRefresh {
    return new CrossTabRefresh(locks as unknown as LockManager, bus.channel());
  }

  it('without Web Locks, runs the refresh as it is', () => {
    const refresh = new FakeRefresh();

    expect(new CrossTabRefresh(null, null).run(refresh.request$)).toBe(refresh.request$);
  });

  it('a tab that waited reuses the token the other tab got, without sending its own refresh', async () => {
    const first = new FakeRefresh();
    const second = new FakeRefresh();
    const tokens: string[] = [];
    tab()
      .run(first.request$)
      .subscribe((r) => tokens.push(`A:${r.accessToken}`));
    tab()
      .run(second.request$)
      .subscribe((r) => tokens.push(`B:${r.accessToken}`));
    await settle();

    // Tab B is waiting for the lock: its refresh hasn't been sent
    expect(first.calls).toBe(1);
    expect(second.calls).toBe(0);

    first.succeed('rotated');
    await settle();

    expect(second.calls).toBe(0);
    expect(tokens).toEqual(['A:rotated', 'B:rotated']);
  });

  it('refreshes after the other tab when its token arrives late, with the cookie already rotated', async () => {
    const first = new FakeRefresh();
    const second = new FakeRefresh();
    const tokens: string[] = [];
    bus.deliver = false;
    tab().run(first.request$).subscribe();
    tab()
      .run(second.request$)
      .subscribe((r) => tokens.push(r.accessToken));
    await settle();
    expect(second.calls).toBe(0);

    first.succeed('rotated');
    await settle();

    // Sent only once the first refresh is over, never at the same time
    expect(second.calls).toBe(1);
    second.succeed('rotated-again');
    await settle();
    expect(tokens).toEqual(['rotated-again']);
  });

  it('does not reuse a token from a refresh that ended before this tab asked', async () => {
    const earlier = new FakeRefresh();
    const later = new FakeRefresh();
    const other = tab();
    const self = tab();
    other.run(earlier.request$).subscribe();
    await settle();
    earlier.succeed('old');
    await settle();

    self.run(later.request$).subscribe();
    await settle();

    expect(later.calls).toBe(1);
  });

  it('a failed refresh releases the lock, and the waiting tab tries on its own', async () => {
    const first = new FakeRefresh();
    const second = new FakeRefresh();
    let firstError: unknown;
    const tokens: string[] = [];
    tab()
      .run(first.request$)
      .subscribe({ error: (error: unknown) => (firstError = error) });
    tab()
      .run(second.request$)
      .subscribe((r) => tokens.push(r.accessToken));
    await settle();
    expect(second.calls).toBe(0);

    first.fail();
    await settle();

    expect(firstError).toEqual(new Error('401'));
    expect(second.calls).toBe(1);
    second.succeed('own');
    await settle();
    expect(tokens).toEqual(['own']);
  });
});
