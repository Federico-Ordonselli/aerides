import { Routes } from '@angular/router';

import { providePaginatorTranslations } from '../../core/i18n/translated-paginator-intl';
import { groupRoleGuard } from '../groups/group-role.guard';

export const AUDIT_ROUTES: Routes = [
  {
    path: '',
    canActivate: [groupRoleGuard('OFFICER')],
    providers: [providePaginatorTranslations()],
    loadComponent: () => import('./audit-log.component').then((m) => m.AuditLogComponent),
  },
];
