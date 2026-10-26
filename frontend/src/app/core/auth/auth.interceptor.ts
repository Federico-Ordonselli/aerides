import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Adds the bearer token to API calls. When a call fails with 401 (expired access token),
 * it refreshes the token once and replays the original request. If the refresh fails too,
 * the session is over and the user is sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  // Login, register and refresh must not trigger a refresh themselves
  if (request.url.startsWith('/api/auth/')) {
    return next(request);
  }

  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getAccessToken();

  return next(token ? withBearer(request, token) : request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !token) {
        return throwError(() => error);
      }
      return authService.refresh().pipe(
        catchError(() => {
          void router.navigate(['/login']);
          return throwError(() => error);
        }),
        switchMap((response) => next(withBearer(request, response.accessToken))),
      );
    }),
  );
};

function withBearer(request: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
