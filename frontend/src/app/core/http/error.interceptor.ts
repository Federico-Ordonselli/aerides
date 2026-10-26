import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';

/**
 * Shows a snackbar for errors that a page cannot handle in a meaningful way: network down, forbidden, server errors.
 * Errors that belong to a form (400 validation, 409 conflict, 422 business rule) and 401 (handled by
 * authInterceptor) are left to the caller. The error is always re-thrown.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const snackBar = inject(MatSnackBar);
  const translate = inject(TranslateService);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const key = toastKeyFor(error.status);
        if (key) {
          snackBar.open(translate.instant(key), translate.instant('common.close'), { duration: 6000 });
        }
      }
      return throwError(() => error);
    }),
  );
};

function toastKeyFor(status: number): string | null {
  if (status === 0) {
    return 'errors.network';
  }
  if (status === 403) {
    return 'errors.forbidden';
  }
  if (status >= 500) {
    return 'errors.server';
  }
  return null;
}
