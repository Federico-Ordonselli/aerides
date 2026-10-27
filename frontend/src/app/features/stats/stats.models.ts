/** Mirrors the backend GroupStatsResponse. */
export interface GroupStats {
  from: string;
  to: string;
  zone: string;
  attendance: MemberAttendance[];
  eventsPerMonth: MonthlyEventCount[];
  unfilledRoles: UnfilledRole[];
}

export interface MemberAttendance {
  userId: number;
  displayName: string;
  /** Completed events of the group since the member joined. */
  completedEvents: number;
  /** Of those, the ones where the member had a confirmed seat. */
  attended: number;
  withdrawals: number;
  /** attended / completedEvents (0..1); null when there was nothing to attend. */
  attendanceRate: number | null;
}

export interface MonthlyEventCount {
  /** "YYYY-MM" */
  month: string;
  completed: number;
  cancelled: number;
}

export interface UnfilledRole {
  roleName: string;
  events: number;
  capacity: number;
  confirmed: number;
  missing: number;
}

/** Periods offered on the stats page, in months (the current month included). */
export const STATS_PERIODS = [3, 6, 12] as const;
export type StatsPeriod = (typeof STATS_PERIODS)[number];

/** First day (local midnight) of the month that starts a period of `months` months ending this month. */
export function periodStart(months: number, today: Date = new Date()): Date {
  return new Date(today.getFullYear(), today.getMonth() - (months - 1), 1);
}

/** True if nothing was completed or cancelled in the period: the page shows an empty state instead of charts. */
export function hasNoEvents(stats: GroupStats): boolean {
  return stats.eventsPerMonth.every((month) => month.completed === 0 && month.cancelled === 0);
}
