import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { GroupsService } from '../groups/groups.service';
import { monthlyChartData } from './stats-chart';
import { hasNoEvents, periodStart, STATS_PERIODS, StatsPeriod } from './stats.models';
import { StatsService } from './stats.service';

/** Group dashboard: attendance per member, events per month and the roles that most often stay empty. */
@Component({
  selector: 'app-group-stats',
  imports: [
    DecimalPipe,
    PercentPipe,
    RouterLink,
    TranslatePipe,
    BaseChartDirective,
    MatButtonToggleModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './group-stats.component.html',
  styleUrl: './group-stats.component.scss',
})
export class GroupStatsComponent {
  private readonly statsService = inject(StatsService);
  private readonly groupsService = inject(GroupsService);
  private readonly translate = inject(TranslateService);
  protected readonly languageService = inject(LanguageService);

  /** Bound from the ":groupId" route parameter. */
  readonly groupId = input.required({ transform: numberAttribute });

  protected readonly periods = STATS_PERIODS;
  protected readonly period = signal<StatsPeriod>(12);
  protected readonly attendanceColumns = ['member', 'attended', 'rate', 'withdrawals'];

  protected readonly group = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.get(params),
  });
  protected readonly stats = rxResource({
    params: () => ({ groupId: this.groupId(), from: periodStart(this.period()) }),
    stream: ({ params }) => this.statsService.get(params.groupId, params.from),
  });

  protected readonly isEmpty = computed(() => this.stats.hasValue() && hasNoEvents(this.stats.value()));

  // Recomputed when the stats or the language change (the month names and legend are translated)
  protected readonly chartData = computed(() => {
    if (!this.stats.hasValue()) {
      return null;
    }
    const language = this.languageService.current();
    return monthlyChartData(
      this.stats.value().eventsPerMonth,
      language,
      {
        completed: this.translate.instant('stats.months.completed'),
        cancelled: this.translate.instant('stats.months.cancelled'),
      },
      { completed: themeColor('--mat-sys-primary', '#6750a4'), cancelled: themeColor('--mat-sys-error', '#b3261e') },
    );
  });

  protected readonly chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
  };
}

/**
 * Chart.js draws on a canvas and can't read CSS variables, so the theme colour is resolved here.
 * The raw variable is not enough: Material defines it as "light-dark(#7d00fa, #d5baff)", which only
 * the browser understands. Applied to a real element, it comes back as a plain "rgb(...)".
 */
function themeColor(variable: string, fallback: string): string {
  const probe = document.createElement('span');
  probe.style.color = `var(${variable}, ${fallback})`;
  document.body.appendChild(probe);
  const color = getComputedStyle(probe).color;
  probe.remove();
  return color || fallback;
}
