import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { GameSearchComponent, SEARCH_DEBOUNCE_MS } from './game-search.component';
import { GamesService } from './games.service';

describe('GameSearchComponent', () => {
  const search = jest.fn();
  let input: HTMLInputElement;
  let host: HTMLElement;

  beforeEach(() => {
    // The app is zoneless, so fakeAsync (which needs zone.js) is not available: Jest fake timers
    // control the setTimeout that debounceTime uses under the hood
    jest.useFakeTimers();
    search.mockReset().mockReturnValue(of([]));
    TestBed.configureTestingModule({
      imports: [GameSearchComponent],
      providers: [provideTranslateService(), { provide: GamesService, useValue: { search } }],
    });
    const fixture = TestBed.createComponent(GameSearchComponent);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
    input = host.querySelector('[data-testid="game-search-input"]') as HTMLInputElement;
  });

  afterEach(() => jest.useRealTimers());

  function type(text: string): void {
    input.value = text;
    input.dispatchEvent(new Event('input'));
  }

  it('waits until the user stops typing, then searches once with the last text', () => {
    type('wi');
    type('wit');
    type('witc');
    jest.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 1);
    expect(search).not.toHaveBeenCalled();

    jest.advanceTimersByTime(1);
    expect(search).toHaveBeenCalledTimes(1);
    expect(search).toHaveBeenCalledWith('witc');
  });

  it('does not search for fewer than 2 characters', () => {
    type('w');
    jest.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(search).not.toHaveBeenCalled();
  });

  it('does not repeat the same search', () => {
    type('zelda');
    jest.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    type('zelda ');
    jest.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(search).toHaveBeenCalledTimes(1);
  });

  it('keeps working after a failed search', () => {
    search.mockReturnValueOnce(throwError(() => new Error('503')));
    type('zelda');
    jest.advanceTimersByTime(SEARCH_DEBOUNCE_MS);

    type('mario');
    jest.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(search).toHaveBeenLastCalledWith('mario');
  });
});
