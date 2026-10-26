import { Routes } from '@angular/router';

import { groupRoleGuard } from './group-role.guard';

/**
 * Routes of the groups feature, loaded lazily from app.routes.ts with loadChildren.
 * Keeping the guard here (instead of in app.routes.ts) keeps it and GroupsService out of the
 * initial bundle: they are downloaded only when the user first opens /groups.
 */
export const GROUP_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./group-list.component').then((m) => m.GroupListComponent),
  },
  {
    path: ':groupId',
    loadComponent: () => import('./group-detail.component').then((m) => m.GroupDetailComponent),
  },
  {
    path: ':groupId/settings',
    canActivate: [groupRoleGuard('OFFICER')],
    loadComponent: () => import('./group-settings.component').then((m) => m.GroupSettingsComponent),
  },
];
