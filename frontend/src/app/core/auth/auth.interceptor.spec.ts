import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { authResponse } from './auth.testing';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let http: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  function logIn(token = 'old-token'): void {
    authService.login({ email: 'mario@example.com', password: 'supersecret' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse(token));
  }

  it('adds the bearer token to API requests', () => {
    logIn('old-token');

    httpClient.get('/api/users/me').subscribe();

    const request = http.expectOne('/api/users/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer old-token');
    request.flush({});
  });

  it('does not add the token to auth endpoints', () => {
    logIn();

    httpClient.post('/api/auth/logout', null).subscribe();

    const request = http.expectOne('/api/auth/logout');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null);
  });

  it('refreshes the token on 401 and replays the request', () => {
    logIn('old-token');
    let body: unknown;

    httpClient.get('/api/users/me').subscribe((response) => (body = response));
    http.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(authResponse('new-token'));

    const retried = http.expectOne('/api/users/me');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
    retried.flush({ ok: true });
    expect(body).toEqual({ ok: true });
  });

  it('uses one refresh for several requests failing at the same time', () => {
    logIn();

    httpClient.get('/api/a').subscribe();
    httpClient.get('/api/b').subscribe();
    http.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    // expectOne fails if two refresh requests were sent
    http.expectOne('/api/auth/refresh').flush(authResponse('new-token'));
    http.expectOne('/api/a').flush({});
    http.expectOne('/api/b').flush({});
  });

  it('redirects to login when the refresh fails', () => {
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    logIn();
    let status: number | undefined;

    httpClient.get('/api/users/me').subscribe({ error: (error) => (status = error.status) });
    http.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(authService.isAuthenticated()).toBe(false);
  });
});
