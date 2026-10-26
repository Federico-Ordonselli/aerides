import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { hasRole, MembershipRole } from './group.models';
import { GroupsService } from './groups.service';

/**
 * Route guard factory: `canActivate: [groupRoleGuard('OFFICER')]` on a route with a `:groupId` param.
 * Users without the role are sent back to the group page; non-members (404) to the group list.
 *
 * This only improves the user experience. The real protection is on the backend, which checks
 * the role on every request.
 */
export function groupRoleGuard(required: MembershipRole): CanActivateFn {
  return (route) => {
    const groupsService = inject(GroupsService);
    const router = inject(Router);
    const groupId = Number(route.paramMap.get('groupId'));

    return groupsService.get(groupId).pipe(
      map((group) => (hasRole(group.myRole, required) ? true : router.createUrlTree(['/groups', groupId]))),
      catchError(() => of(router.createUrlTree(['/groups']))),
    );
  };
}
