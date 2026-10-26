import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageService } from '../../core/i18n/language.service';
import { Member, MembershipRole } from './group.models';

export interface RoleChange {
  member: Member;
  role: MembershipRole;
}

/**
 * Members of a group. Presentational only: it shows data and emits events, the parent calls the API.
 * The OWNER sees a role selector and a remove button on every row except their own.
 */
@Component({
  selector: 'app-members-table',
  imports: [DatePipe, TranslatePipe, MatTableModule, MatSelectModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './members-table.component.html',
  styleUrl: './members-table.component.scss',
})
export class MembersTableComponent {
  protected readonly languageService = inject(LanguageService);

  readonly members = input.required<Member[]>();
  readonly myRole = input.required<MembershipRole>();
  readonly myUserId = input.required<number>();

  readonly roleChange = output<RoleChange>();
  readonly remove = output<Member>();

  protected readonly assignableRoles: MembershipRole[] = ['MEMBER', 'OFFICER'];
  protected readonly isOwner = computed(() => this.myRole() === 'OWNER');
  protected readonly columns = computed(() =>
    this.isOwner() ? ['name', 'role', 'joinedAt', 'actions'] : ['name', 'role', 'joinedAt'],
  );

  /** The owner manages everyone except themselves (the only OWNER row is theirs). */
  protected canManage(member: Member): boolean {
    return this.isOwner() && member.userId !== this.myUserId();
  }
}
