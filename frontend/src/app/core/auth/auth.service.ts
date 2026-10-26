import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, map, Observable, of, share, tap, throwError } from 'rxjs';

import { AuthResponse, LoginRequest, RegisterRequest, User } from './auth.models';

const AUTH_API = '/api/auth';

/**
 * Holds the session state in signals.
 *
 * The access token lives only in memory (never in localStorage), so an XSS script cannot read it
 * from storage. After a page reload the session is restored by calling /refresh: the browser sends
 * the HttpOnly refresh cookie automatically.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly accessToken = signal<string | null>(null);
  private readonly user = signal<User | null>(null);

  /** Shared by all callers while a refresh request is running, so only one is sent. */
  private refreshInFlight$: Observable<AuthResponse> | null = null;

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);

  getAccessToken(): string | null {
    return this.accessToken();
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${AUTH_API}/login`, request).pipe(
      tap((response) => this.startSession(response)),
      map((response) => response.user),
    );
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${AUTH_API}/register`, request).pipe(
      tap((response) => this.startSession(response)),
      map((response) => response.user),
    );
  }

  /**
   * Gets a new access token using the refresh cookie. If several requests fail with 401 at the same time,
   * they all wait for the same refresh call: sending two would trigger the backend's reuse detection.
   */
  refresh(): Observable<AuthResponse> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<AuthResponse>(`${AUTH_API}/refresh`, null).pipe(
        tap((response) => this.startSession(response)),
        catchError((error: unknown) => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => (this.refreshInFlight$ = null)),
        share(),
      );
    }
    return this.refreshInFlight$;
  }

  /** Called once at startup: restores the session if a valid refresh cookie exists. Never fails. */
  restoreSession(): Observable<void> {
    return this.refresh().pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  logout(): void {
    this.http
      .post<void>(`${AUTH_API}/logout`, null)
      .pipe(
        catchError(() => of(undefined)),
        finalize(() => {
          this.clearSession();
          void this.router.navigate(['/login']);
        }),
      )
      .subscribe();
  }

  private startSession(response: AuthResponse): void {
    this.accessToken.set(response.accessToken);
    this.user.set(response.user);
  }

  private clearSession(): void {
    this.accessToken.set(null);
    this.user.set(null);
  }
}
