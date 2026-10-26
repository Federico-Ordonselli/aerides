import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, isObservable, Observable, of, throwError } from 'rxjs';

import { GroupDetail, hasRole, MembershipRole } from './group.models';
import { groupRoleGuard } from './group-role.guard';
import { GroupsService } from './groups.service';

describe('hasRole', () => {
  it('follows OWNER > OFFICER > MEMBER', () => {
    expect(hasRole('OWNER', 'OFFICER')).toBe(true);
    expect(hasRole('OFFICER', 'OFFICER')).toBe(true);
    expect(hasRole('MEMBER', 'OFFICER')).toBe(false);
    expect(hasRole(undefined, 'MEMBER')).toBe(false);
  });
});

describe('groupRoleGuard', () => {
  const get = jest.fn();
  const route = { paramMap: convertToParamMap({ groupId: '42' }) } as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  beforeEach(() => {
    get.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: GroupsService, useValue: { get } }],
    });
  });

  async function run(required: MembershipRole): Promise<boolean | string> {
    const result = TestBed.runInInjectionContext(() => groupRoleGuard(required)(route, state));
    const value = isObservable(result) ? await firstValueFrom(result as Observable<boolean | UrlTree>) : result;
    return value instanceof UrlTree ? TestBed.inject(Router).serializeUrl(value) : (value as boolean);
  }

  function groupWithRole(myRole: MembershipRole): Observable<GroupDetail> {
    return of({ id: 42, myRole } as GroupDetail);
  }

  it('lets an officer in', async () => {
    get.mockReturnValue(groupWithRole('OFFICER'));
    expect(await run('OFFICER')).toBe(true);
    expect(get).toHaveBeenCalledWith(42);
  });

  it('sends a member back to the group page', async () => {
    get.mockReturnValue(groupWithRole('MEMBER'));
    expect(await run('OFFICER')).toBe('/groups/42');
  });

  it('sends a non-member (404) to the group list', async () => {
    get.mockReturnValue(throwError(() => ({ status: 404 })));
    expect(await run('OFFICER')).toBe('/groups');
  });
});
