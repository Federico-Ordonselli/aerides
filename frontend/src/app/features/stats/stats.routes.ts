import { Routes } from '@angular/router';
import { BarController, BarElement, CategoryScale, Legend, LinearScale, Tooltip } from 'chart.js';
import { provideCharts } from 'ng2-charts';

/**
 * Chart.js is registered here, on the lazy route, and not in app.config.ts: its code then lands in
 * this feature's chunk and is downloaded only by users who open the stats page.
 * Only the pieces of Chart.js a bar chart needs are registered, which keeps the chunk small.
 */
export const STATS_ROUTES: Routes = [
  {
    path: '',
    providers: [provideCharts({ registerables: [BarController, BarElement, CategoryScale, LinearScale, Legend, Tooltip] })],
    loadComponent: () => import('./group-stats.component').then((m) => m.GroupStatsComponent),
  },
];
