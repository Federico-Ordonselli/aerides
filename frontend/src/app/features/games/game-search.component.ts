import { Component, inject, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, debounceTime, distinctUntilChanged, map, Observable, of, switchMap, tap } from 'rxjs';

import { GameSearchResult } from './game.models';
import { GamesService } from './games.service';

export const SEARCH_DEBOUNCE_MS = 300;
const MIN_QUERY_LENGTH = 2;

/**
 * Search box for the RAWG catalog, with suggestions while typing.
 *
 * The RxJS pipeline sends a request only when the user stops typing for 300 ms (debounceTime),
 * skips repeated queries (distinctUntilChanged), and cancels the previous request when a new one
 * starts (switchMap), so an old slow answer can never overwrite a newer one.
 */
@Component({
  selector: 'app-game-search',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './game-search.component.html',
  styleUrl: './game-search.component.scss',
})
export class GameSearchComponent {
  private readonly gamesService = inject(GamesService);

  readonly gameSelected = output<GameSearchResult>();

  // The value is a string while typing, and the chosen object for a moment after a selection
  protected readonly query = new FormControl<string | GameSearchResult>('', { nonNullable: true });
  protected readonly loading = signal(false);
  protected readonly unavailable = signal(false);

  protected readonly results = toSignal(
    this.query.valueChanges.pipe(
      map((value) => (typeof value === 'string' ? value.trim() : '')),
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((text) => this.search(text)),
    ),
    { initialValue: [] as GameSearchResult[] },
  );

  protected select(event: MatAutocompleteSelectedEvent): void {
    this.gameSelected.emit(event.option.value as GameSearchResult);
    this.query.setValue('');
  }

  private search(text: string): Observable<GameSearchResult[]> {
    if (text.length < MIN_QUERY_LENGTH) {
      return of([]);
    }
    this.loading.set(true);
    this.unavailable.set(false);
    return this.gamesService.search(text).pipe(
      tap(() => this.loading.set(false)),
      // An error must not end the stream, or the search box would stop working: replace it with no results
      catchError(() => {
        this.loading.set(false);
        this.unavailable.set(true);
        return of([]);
      }),
    );
  }
}
