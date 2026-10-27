import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { BaseChartDirective } from 'ng2-charts';
import { of } from 'rxjs';

import { GroupsService } from '../groups/groups.service';
import { GroupStatsComponent } from './group-stats.component';
import { GroupStats } from './stats.models';
import { StatsService } from './stats.service';

const STATS: GroupStats = {
  from: '2025-11-01T00:00:00Z',
  to: '2026-10-27T10:00:00Z',
  zone: 'Europe/Rome',
  attendance: [
    { userId: 1, displayName: 'Giulia', completedEvents: 2, attended: 1, withdrawals: 0, attendanceRate: 0.5 },
    { userId: 2, displayName: 'Newbie', completedEvents: 0, attended: 0, withdrawals: 1, attendanceRate: null },
  ],
  eventsPerMonth: [{ month: '2026-09', completed: 2, cancelled: 0 }],
  unfilledRoles: [{ roleName: 'Healer', events: 2, capacity: 3, confirmed: 1, missing: 2 }],
};

describe('GroupStatsComponent', () => {
  let statsService: { get: jest.Mock };

  async function render(): Promise<ComponentFixture<GroupStatsComponent>> {
    TestBed.configureTestingModule({
      imports: [GroupStatsComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: StatsService, useValue: statsService },
        { provide: GroupsService, useValue: { get: () => of({ id: 1, name: 'Gilda' }) } },
      ],
    });
    // jsdom has no canvas: the chart directive is left out, its data is tested in stats.models.spec.ts
    TestBed.overrideComponent(GroupStatsComponent, {
      remove: { imports: [BaseChartDirective] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(GroupStatsComponent);
    fixture.componentRef.setInput('groupId', 1);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    statsService = { get: jest.fn(() => of(STATS)) };
  });

  it('shows attendance per member, with a dash when there was nothing to attend', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    const rows = [...element.querySelectorAll('[data-testid="attendance-table"] tr.mat-mdc-row')];
    expect(rows.map((row) => row.querySelector('td')!.textContent!.trim())).toEqual(['Giulia', 'Newbie']);
    expect(rows[0].textContent).toContain('1 / 2');
    expect(rows[0].textContent).toContain('50%');
    expect(rows[1].textContent).toContain('—');
    expect(element.querySelector('[data-testid="unfilled-roles"]')!.textContent).toContain('Healer');
  });

  it('shows an empty state when nothing was completed or cancelled in the period', async () => {
    statsService.get.mockReturnValue(of({ ...STATS, eventsPerMonth: [{ month: '2026-09', completed: 0, cancelled: 0 }] }));
    const element = (await render()).nativeElement as HTMLElement;

    expect(element.textContent).toContain('stats.emptyTitle');
    expect(element.querySelector('[data-testid="attendance-table"]')).toBeNull();
  });

  it('asks for a shorter period when another one is chosen', async () => {
    const fixture = await render();
    const firstFrom: Date = statsService.get.mock.calls[0][1];

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="period-3"] button')!.click();
    await fixture.whenStable();

    const lastFrom: Date = statsService.get.mock.lastCall[1];
    expect(lastFrom.getTime()).toBeGreaterThan(firstFrom.getTime());
    expect(lastFrom.getDate()).toBe(1);
  });
});
