import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GroupsService } from './groups.service';

describe('GroupsService', () => {
  let service: GroupsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(GroupsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('joins with an invite code', () => {
    service.join('ABCDEFGHJK').subscribe();
    const request = http.expectOne({ method: 'POST', url: '/api/groups/join' });
    expect(request.request.body).toEqual({ inviteCode: 'ABCDEFGHJK' });
    request.flush({});
  });

  it('removes the Discord webhook by sending url = null', () => {
    service.updateDiscordWebhook(7, null).subscribe();
    const request = http.expectOne({ method: 'PUT', url: '/api/groups/7/discord-webhook' });
    expect(request.request.body).toEqual({ url: null });
    request.flush(null);
  });

  it('extracts the new code when regenerating the invite code', () => {
    let code: string | undefined;
    service.regenerateInviteCode(7).subscribe((value) => (code = value));
    http.expectOne({ method: 'POST', url: '/api/groups/7/invite-code' }).flush({ inviteCode: 'NEWCODE234' });
    expect(code).toBe('NEWCODE234');
  });

  it('leaves a group through the /members/me endpoint', () => {
    service.leave(7).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/groups/7/members/me' }).flush(null);
  });
});
