import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { GroupStats } from './stats.models';

@Injectable({ providedIn: 'root' })
export class StatsService {
  private readonly http = inject(HttpClient);

  /**
   * Stats from `from` until now. The browser's time zone is sent so that the backend groups
   * events by month the way the user sees the calendar (an event at 00:30 on 1 November in Rome
   * is still 31 October in UTC).
   */
  get(groupId: number, from: Date): Observable<GroupStats> {
    const params = new HttpParams()
      .set('from', from.toISOString())
      .set('zone', Intl.DateTimeFormat().resolvedOptions().timeZone);
    return this.http.get<GroupStats>(`/api/groups/${groupId}/stats`, { params });
  }
}
