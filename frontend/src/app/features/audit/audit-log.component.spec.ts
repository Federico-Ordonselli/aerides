import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { Page } from '../../shared/models/page.models';
import { GroupsService } from '../groups/groups.service';
import { AuditLogComponent } from './audit-log.component';
import { AuditEntry } from './audit.models';
import { AuditService } from './audit.service';

const ENTRY: AuditEntry = {
  id: 2,
  action: 'MEMBER_ROLE_CHANGED',
  entityType: 'MEMBERSHIP',
  entityId: 5,
  actorId: null,
  actorName: null,
  details: { userId: 7, from: 'MEMBER', to: 'OFFICER' },
  createdAt: '2026-10-01T18:00:00Z',
};

function page(content: AuditEntry[]): Page<AuditEntry> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('AuditLogComponent', () => {
  let auditService: { list: jest.Mock };

  async function render(): Promise<ComponentFixture<AuditLogComponent>> {
    TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: AuditService, useValue: auditService },
        {
          provide: GroupsService,
          useValue: {
            get: () => of({ id: 1, name: 'Gilda' }),
            members: () => of([{ userId: 7, displayName: 'Marco' }]),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(AuditLogComponent);
    fixture.componentRef.setInput('groupId', 1);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    auditService = { list: jest.fn(() => of(page([ENTRY]))) };
  });

  it('shows each entry with its action, the member names and a placeholder for deleted actors', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    const row = element.querySelector('[data-testid="audit-row"]')!;
    // No translations loaded in tests: the pipe returns the key
    expect(row.textContent).toContain('audit.actions.MEMBER_ROLE_CHANGED');
    expect(row.textContent).toContain('audit.deletedUser');
    expect(row.textContent).toContain('Marco');
    expect(auditService.list).toHaveBeenCalledWith(1, null, { page: 0, size: 20 });
  });

  it('goes back to the first page when the filter changes', async () => {
    const fixture = await render();
    const protectedApi = fixture.componentInstance as unknown as {
      pageChanged(event: { pageIndex: number; pageSize: number }): void;
      filterBy(action: string): void;
    };

    protectedApi.pageChanged({ pageIndex: 2, pageSize: 50 });
    protectedApi.filterBy('GAME_ADDED');
    await fixture.whenStable();

    expect(auditService.list).toHaveBeenLastCalledWith(1, 'GAME_ADDED', { page: 0, size: 50 });
  });

  it('offers a retry when the log cannot be loaded', async () => {
    auditService.list.mockReturnValueOnce(throwError(() => new Error('offline')));
    const fixture = await render();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="retry"]')!.click();
    await fixture.whenStable();

    expect(auditService.list).toHaveBeenCalledTimes(2);
  });
});
