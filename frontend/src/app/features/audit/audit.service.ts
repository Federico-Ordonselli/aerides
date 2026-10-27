import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from '../../shared/models/page.models';
import { AuditAction, AuditEntry } from './audit.models';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  /** Newest first; the order is fixed by the backend. */
  list(groupId: number, action: AuditAction | null, page: PageRequest): Observable<Page<AuditEntry>> {
    let params = new HttpParams().set('page', page.page).set('size', page.size);
    if (action) {
      params = params.set('action', action);
    }
    return this.http.get<Page<AuditEntry>>(`/api/groups/${groupId}/audit`, { params });
  }
}
