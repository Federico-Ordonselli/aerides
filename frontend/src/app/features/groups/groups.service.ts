import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { GroupDetail, GroupRequest, GroupSummary, Member, MembershipRole } from './group.models';

const API = '/api/groups';

@Injectable({ providedIn: 'root' })
export class GroupsService {
  private readonly http = inject(HttpClient);

  listMine(): Observable<GroupSummary[]> {
    return this.http.get<GroupSummary[]>(API);
  }

  get(groupId: number): Observable<GroupDetail> {
    return this.http.get<GroupDetail>(`${API}/${groupId}`);
  }

  create(request: GroupRequest): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(API, request);
  }

  update(groupId: number, request: GroupRequest): Observable<GroupDetail> {
    return this.http.patch<GroupDetail>(`${API}/${groupId}`, request);
  }

  delete(groupId: number): Observable<void> {
    return this.http.delete<void>(`${API}/${groupId}`);
  }

  join(inviteCode: string): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${API}/join`, { inviteCode });
  }

  regenerateInviteCode(groupId: number): Observable<string> {
    return this.http
      .post<{ inviteCode: string }>(`${API}/${groupId}/invite-code`, null)
      .pipe(map((response) => response.inviteCode));
  }

  /** `url = null` removes the webhook. */
  updateDiscordWebhook(groupId: number, url: string | null): Observable<void> {
    return this.http.put<void>(`${API}/${groupId}/discord-webhook`, { url });
  }

  members(groupId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`${API}/${groupId}/members`);
  }

  changeRole(groupId: number, userId: number, role: MembershipRole): Observable<Member> {
    return this.http.patch<Member>(`${API}/${groupId}/members/${userId}`, { role });
  }

  removeMember(groupId: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${API}/${groupId}/members/${userId}`);
  }

  leave(groupId: number): Observable<void> {
    return this.http.delete<void>(`${API}/${groupId}/members/me`);
  }
}
