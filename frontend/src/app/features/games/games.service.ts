import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { SKIP_ERROR_SNACKBAR } from '../../core/http/http-context-tokens';
import { Game, GameSearchResult } from './game.models';

@Injectable({ providedIn: 'root' })
export class GamesService {
  private readonly http = inject(HttpClient);

  search(query: string): Observable<GameSearchResult[]> {
    // The search box shows its own "unavailable" message, so no global snackbar
    return this.http.get<GameSearchResult[]>('/api/games/search', {
      params: { q: query },
      context: new HttpContext().set(SKIP_ERROR_SNACKBAR, true),
    });
  }

  list(groupId: number): Observable<Game[]> {
    return this.http.get<Game[]>(`/api/groups/${groupId}/games`);
  }

  add(groupId: number, rawgId: number): Observable<Game> {
    return this.http.post<Game>(`/api/groups/${groupId}/games`, { rawgId });
  }

  remove(groupId: number, gameId: number): Observable<void> {
    return this.http.delete<void>(`/api/groups/${groupId}/games/${gameId}`);
  }
}
