import { inject, Injectable, Provider } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateService } from '@ngx-translate/core';

/**
 * Angular Material's paginator has its own labels ("Items per page", "1 – 20 of 57"), in English only.
 * This replaces them with our translations, and updates them when the user switches language.
 *
 * Provided on the lazy routes that show a paginator (see {@link providePaginatorTranslations}), not in
 * app.config.ts: importing anything from @angular/material/paginator there would pull the whole
 * paginator, with its select and form field, into the initial bundle.
 */
@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl {
  private readonly translate = inject(TranslateService);

  constructor() {
    super();
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => this.updateLabels());
    this.updateLabels();
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0) {
      return this.translate.instant('paginator.empty');
    }
    const start = page * pageSize + 1;
    const end = Math.min((page + 1) * pageSize, length);
    return this.translate.instant('paginator.range', { start, end, length });
  };

  private updateLabels(): void {
    this.itemsPerPageLabel = this.translate.instant('paginator.itemsPerPage');
    this.nextPageLabel = this.translate.instant('paginator.next');
    this.previousPageLabel = this.translate.instant('paginator.previous');
    this.firstPageLabel = this.translate.instant('paginator.first');
    this.lastPageLabel = this.translate.instant('paginator.last');
    // Tells every paginator on screen to render again with the new labels
    this.changes.next();
  }
}

/** Route-level provider: `providers: [providePaginatorTranslations()]`. */
export function providePaginatorTranslations(): Provider {
  return { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl };
}
