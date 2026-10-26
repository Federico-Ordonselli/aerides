import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EventsService } from './events.service';

describe('EventsService', () => {
  let service: EventsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EventsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the filters that are set, as query parameters', () => {
    service
      .list(7, { from: new Date('2027-03-01T00:00:00Z'), status: 'PUBLISHED', gameId: null }, { page: 2, size: 10, sort: 'startsAt,desc' })
      .subscribe();

    const request = http.expectOne((req) => req.url === '/api/groups/7/events');
    expect(request.request.params.keys().sort()).toEqual(['from', 'page', 'size', 'sort', 'status']);
    expect(request.request.params.get('from')).toBe('2027-03-01T00:00:00.000Z');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('sort')).toBe('startsAt,desc');
    request.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('signs up for a slot', () => {
    service.signUp(7, 3, 11).subscribe();
    const request = http.expectOne({ method: 'POST', url: '/api/groups/7/events/3/signups' });
    expect(request.request.body).toEqual({ slotId: 11 });
    request.flush({});
  });

  it('withdraws through the /signups/me endpoint', () => {
    service.withdraw(7, 3).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/groups/7/events/3/signups/me' }).flush(null);
  });

  it('approves a sign-up', () => {
    service.approve(7, 3, 99).subscribe();
    http.expectOne({ method: 'POST', url: '/api/groups/7/events/3/signups/99/approve' }).flush({});
  });
});
