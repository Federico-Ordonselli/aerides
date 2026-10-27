import { Routes } from '@angular/router';

import { providePaginatorTranslations } from '../../core/i18n/translated-paginator-intl';
import { groupRoleGuard } from '../groups/group-role.guard';

/** Mounted under /groups/:groupId/events. Creating and editing are for OFFICER and OWNER only. */
export const EVENT_ROUTES: Routes = [
  {
    path: '',
    providers: [providePaginatorTranslations()],
    loadComponent: () => import('./event-list.component').then((m) => m.EventListComponent),
  },
  {
    path: 'new',
    canActivate: [groupRoleGuard('OFFICER')],
    loadComponent: () => import('./event-form.component').then((m) => m.EventFormComponent),
  },
  {
    path: ':eventId',
    loadComponent: () => import('./event-detail.component').then((m) => m.EventDetailComponent),
  },
  {
    path: ':eventId/edit',
    canActivate: [groupRoleGuard('OFFICER')],
    loadComponent: () => import('./event-form.component').then((m) => m.EventFormComponent),
  },
];
