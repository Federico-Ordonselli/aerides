import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  const authenticated = signal(false);
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/groups/42' } as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated: authenticated } }],
    });
  });

  function run(guard: typeof authGuard) {
    return TestBed.runInInjectionContext(() => guard(route, state));
  }

  it('authGuard lets authenticated users through', () => {
    authenticated.set(true);
    expect(run(authGuard)).toBe(true);
  });

  it('authGuard sends anonymous users to login, keeping the requested url', () => {
    authenticated.set(false);
    const result = run(authGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Fgroups%2F42');
  });

  it('guestGuard sends authenticated users to the home page', () => {
    authenticated.set(true);
    const result = run(guestGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/');
  });

  it('guestGuard lets anonymous users through', () => {
    authenticated.set(false);
    expect(run(guestGuard)).toBe(true);
  });
});
