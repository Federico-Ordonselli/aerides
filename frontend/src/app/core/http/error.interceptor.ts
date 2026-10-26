import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';

/**
 * Shows a toast for errors that a page cannot handle in a meaningful way: network down, forbidden, server errors.
 * Errors that belong to a form (400 validation, 409 conflict, 422 business rule) and 401 (handled by
 * authInterceptor) are left to the caller. The error is always re-thrown.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const messageService = inject(MessageService);
  const translate = inject(TranslateService);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const key = toastKeyFor(error.status);
        if (key) {
          messageService.add({
            severity: 'error',
            summary: translate.instant('errors.title'),
            detail: translate.instant(key),
          });
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
