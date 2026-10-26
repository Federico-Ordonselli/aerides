import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { authResponse } from './auth.testing';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts a session after login', () => {
    let loggedInName: string | undefined;
    service.login({ email: 'mario@example.com', password: 'supersecret' }).subscribe((user) => {
      loggedInName = user.displayName;
    });

    http.expectOne({ method: 'POST', url: '/api/auth/login' }).flush(authResponse('token-from-login'));

    expect(loggedInName).toBe('Mario');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.getAccessToken()).toBe('token-from-login');
  });

  it('sends a single refresh request when called several times in parallel', () => {
    const results: string[] = [];
    service.refresh().subscribe((response) => results.push(response.accessToken));
    service.refresh().subscribe((response) => results.push(response.accessToken));

    http.expectOne('/api/auth/refresh').flush(authResponse('refreshed'));

    expect(results).toEqual(['refreshed', 'refreshed']);
    expect(service.getAccessToken()).toBe('refreshed');
  });

  it('clears the session when the refresh fails', () => {
    service.login({ email: 'mario@example.com', password: 'supersecret' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse());

    service.refresh().subscribe({ error: () => undefined });
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getAccessToken()).toBeNull();
  });

  it('restoreSession completes without error when there is no valid refresh cookie', () => {
    let completed = false;
    service.restoreSession().subscribe({ complete: () => (completed = true) });

    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(completed).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logout clears the session and goes to the login page, even if the server call fails', () => {
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    service.login({ email: 'mario@example.com', password: 'supersecret' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse());

    service.logout();
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(service.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
