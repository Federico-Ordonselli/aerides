import { TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';

import { TranslatedPaginatorIntl } from './translated-paginator-intl';

describe('TranslatedPaginatorIntl', () => {
  let intl: TranslatedPaginatorIntl;
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService(), TranslatedPaginatorIntl] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('it', { paginator: { itemsPerPage: 'Elementi per pagina', range: '{{start}} – {{end}} di {{length}}' } });
    translate.setTranslation('en', { paginator: { itemsPerPage: 'Items per page', range: '{{start}} – {{end}} of {{length}}' } });
    translate.use('it');
    intl = TestBed.inject(TranslatedPaginatorIntl);
  });

  it('uses the current language, also for the range label', () => {
    expect(intl.itemsPerPageLabel).toBe('Elementi per pagina');
    expect(intl.getRangeLabel(2, 20, 57)).toBe('41 – 57 di 57');
  });

  it('updates the labels and notifies the paginators when the language changes', () => {
    const changed = jest.fn();
    intl.changes.subscribe(changed);

    translate.use('en');

    expect(intl.itemsPerPageLabel).toBe('Items per page');
    expect(changed).toHaveBeenCalled();
  });
});
