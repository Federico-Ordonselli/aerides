import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { ErrorStateComponent } from './error-state.component';

describe('ErrorStateComponent', () => {
  it('emits retry when the button is clicked', async () => {
    TestBed.configureTestingModule({ imports: [ErrorStateComponent], providers: [provideTranslateService()] });
    const fixture = TestBed.createComponent(ErrorStateComponent);
    const retry = jest.fn();
    fixture.componentInstance.retry.subscribe(retry);
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="retry"]')!.click();

    expect(retry).toHaveBeenCalledTimes(1);
  });
});
