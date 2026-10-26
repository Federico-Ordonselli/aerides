import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';

// Feature pages are lazy-loaded: each one becomes a separate JS chunk, downloaded on first visit
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'groups',
        loadChildren: () => import('./features/groups/groups.routes').then((m) => m.GROUP_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
