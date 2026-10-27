import { ChartConfiguration } from 'chart.js';

import { MonthlyEventCount } from './stats.models';

export interface ChartLabels {
  completed: string;
  cancelled: string;
}

/**
 * Data for the "events per month" bar chart. Kept out of the component as a pure function,
 * so it can be tested without a canvas (jsdom has none).
 */
export function monthlyChartData(
  months: MonthlyEventCount[],
  locale: string,
  labels: ChartLabels,
  colors: { completed: string; cancelled: string },
): ChartConfiguration<'bar'>['data'] {
  return {
    labels: months.map((month) => monthLabel(month.month, locale)),
    datasets: [
      { label: labels.completed, data: months.map((month) => month.completed), backgroundColor: colors.completed },
      { label: labels.cancelled, data: months.map((month) => month.cancelled), backgroundColor: colors.cancelled },
    ],
  };
}

/** "2026-09" → "set 26" in Italian, "Sep 26" in English. */
export function monthLabel(yearMonth: string, locale: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString(locale, { month: 'short', year: '2-digit' });
}
