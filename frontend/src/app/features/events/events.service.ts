import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from '../../shared/models/page.models';
import {
  CreateEventRequest,
  EventDetail,
  EventFilter,
  EventSummary,
  Signup,
  UpdateEventRequest,
} from './event.models';

@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);

  list(groupId: number, filter: EventFilter, page: PageRequest): Observable<Page<EventSummary>> {
    // HttpParams is immutable: every set() returns a new instance
    let params = new HttpParams().set('page', page.page).set('size', page.size);
    if (page.sort) {
      params = params.set('sort', page.sort);
    }
    if (filter.from) {
      params = params.set('from', filter.from.toISOString());
    }
    if (filter.to) {
      params = params.set('to', filter.to.toISOString());
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.gameId) {
      params = params.set('gameId', filter.gameId);
    }
    return this.http.get<Page<EventSummary>>(eventsUrl(groupId), { params });
  }

  get(groupId: number, eventId: number): Observable<EventDetail> {
    return this.http.get<EventDetail>(`${eventsUrl(groupId)}/${eventId}`);
  }

  /** For a weekly series, returns the first occurrence. */
  create(groupId: number, request: CreateEventRequest): Observable<EventDetail> {
    return this.http.post<EventDetail>(eventsUrl(groupId), request);
  }

  update(groupId: number, eventId: number, request: UpdateEventRequest): Observable<EventDetail> {
    return this.http.put<EventDetail>(`${eventsUrl(groupId)}/${eventId}`, request);
  }

  publish(groupId: number, eventId: number): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${eventsUrl(groupId)}/${eventId}/publish`, null);
  }

  cancel(groupId: number, eventId: number): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${eventsUrl(groupId)}/${eventId}/cancel`, null);
  }

  complete(groupId: number, eventId: number): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${eventsUrl(groupId)}/${eventId}/complete`, null);
  }

  signUp(groupId: number, eventId: number, slotId: number): Observable<Signup> {
    return this.http.post<Signup>(`${eventsUrl(groupId)}/${eventId}/signups`, { slotId });
  }

  withdraw(groupId: number, eventId: number): Observable<void> {
    return this.http.delete<void>(`${eventsUrl(groupId)}/${eventId}/signups/me`);
  }

  approve(groupId: number, eventId: number, signupId: number): Observable<Signup> {
    return this.http.post<Signup>(`${eventsUrl(groupId)}/${eventId}/signups/${signupId}/approve`, null);
  }

  reject(groupId: number, eventId: number, signupId: number): Observable<Signup> {
    return this.http.post<Signup>(`${eventsUrl(groupId)}/${eventId}/signups/${signupId}/reject`, null);
  }
}

function eventsUrl(groupId: number): string {
  return `/api/groups/${groupId}/events`;
}
