import { HttpContextToken } from '@angular/common/http';

/**
 * Set on a request to handle its errors in the component instead of showing the global snackbar:
 * `http.get(url, { context: new HttpContext().set(SKIP_ERROR_SNACKBAR, true) })`.
 *
 * Kept in its own file, away from error.interceptor.ts: lazy-loaded services import it, and importing
 * the interceptor file would drag MatSnackBar and its dependencies into shared bundle chunks.
 */
export const SKIP_ERROR_SNACKBAR = new HttpContextToken<boolean>(() => false);
