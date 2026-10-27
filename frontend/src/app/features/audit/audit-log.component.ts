import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { GroupsService } from '../groups/groups.service';
import { AUDIT_ACTIONS, AuditAction, AuditEntry, describeDetails, DetailItem } from './audit.models';
import { AuditService } from './audit.service';

/** The group's audit log, for OWNER and OFFICER: who changed what, newest first. */
@Component({
  selector: 'app-audit-log',
  imports: [
    DatePipe,
    RouterLink,
    TranslatePipe,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss',
})
export class AuditLogComponent {
  private readonly auditService = inject(AuditService);
  private readonly groupsService = inject(GroupsService);
  protected readonly languageService = inject(LanguageService);

  /** Bound from the ":groupId" route parameter. */
  readonly groupId = input.required({ transform: numberAttribute });

  protected readonly actions = AUDIT_ACTIONS;
  protected readonly columns = ['createdAt', 'actor', 'action', 'details'];
  protected readonly action = signal<AuditAction | null>(null);
  protected readonly pageRequest = signal({ page: 0, size: 20 });

  protected readonly group = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.get(params),
  });
  protected readonly members = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.members(params),
  });
  protected readonly entries = rxResource({
    params: () => ({ groupId: this.groupId(), action: this.action(), page: this.pageRequest() }),
    stream: ({ params }) => this.auditService.list(params.groupId, params.action, params.page),
  });

  /** Names of the current members, to show "Marco" instead of "userId: 7" in the details. */
  private readonly memberNames = computed(
    () => new Map((this.members.hasValue() ? this.members.value() : []).map((m) => [m.userId, m.displayName])),
  );

  protected details(entry: AuditEntry): DetailItem[] {
    return describeDetails(entry.details, this.memberNames(), this.languageService.current());
  }

  protected filterBy(action: AuditAction | null): void {
    this.action.set(action);
    // A filter changes the number of pages: start again from the first one
    this.pageRequest.update((page) => ({ ...page, page: 0 }));
  }

  protected pageChanged(event: PageEvent): void {
    this.pageRequest.set({ page: event.pageIndex, size: event.pageSize });
  }
}
