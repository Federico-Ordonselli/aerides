import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { confirmAction } from '../../shared/components/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { Game, GameSearchResult } from '../games/game.models';
import { GamesService } from '../games/games.service';
import { GroupGamesComponent } from '../games/group-games.component';
import { hasRole, Member } from './group.models';
import { GroupsService } from './groups.service';
import { MembersTableComponent, RoleChange } from './members-table.component';

@Component({
  selector: 'app-group-detail',
  imports: [
    RouterLink,
    TranslatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTabsModule,
    EmptyStateComponent,
    GroupGamesComponent,
    MembersTableComponent,
  ],
  templateUrl: './group-detail.component.html',
  styleUrl: './group-detail.component.scss',
})
export class GroupDetailComponent {
  private readonly groupsService = inject(GroupsService);
  private readonly gamesService = inject(GamesService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  protected readonly authService = inject(AuthService);

  /** Bound from the ":groupId" route parameter (withComponentInputBinding). */
  readonly groupId = input.required({ transform: numberAttribute });

  // rxResource reloads automatically when groupId() changes, e.g. when navigating to another group
  protected readonly group = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.get(params),
  });
  protected readonly members = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.members(params),
  });
  protected readonly games = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.gamesService.list(params),
  });

  protected readonly myRole = computed(() => (this.group.hasValue() ? this.group.value().myRole : undefined));
  protected readonly isOfficer = computed(() => hasRole(this.myRole(), 'OFFICER'));
  protected readonly myUserId = computed(() => this.authService.currentUser()?.id ?? 0);

  protected changeRole({ member, role }: RoleChange): void {
    this.groupsService.changeRole(this.groupId(), member.userId, role).subscribe({
      next: () => this.notify('groups.members.roleChanged', { name: member.displayName }),
      // Reload on error too, so the select goes back to the real role
      error: () => this.members.reload(),
      complete: () => this.members.reload(),
    });
  }

  protected removeMember(member: Member): void {
    confirmAction(this.dialog, {
      title: this.translate.instant('groups.members.removeTitle'),
      message: this.translate.instant('groups.members.removeMessage', { name: member.displayName }),
      confirmLabel: this.translate.instant('groups.members.remove'),
      destructive: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.groupsService.removeMember(this.groupId(), member.userId)),
      )
      .subscribe(() => {
        this.members.reload();
        this.group.reload();
      });
  }

  protected addGame(game: GameSearchResult): void {
    this.gamesService.add(this.groupId(), game.rawgId).subscribe({
      next: () => {
        this.notify('games.added', { name: game.name });
        this.games.reload();
      },
      error: (error: { status?: number }) => {
        if (error.status === 409) {
          this.notify('games.alreadyAdded', { name: game.name });
        }
      },
    });
  }

  protected removeGame(game: Game): void {
    confirmAction(this.dialog, {
      title: this.translate.instant('games.removeTitle'),
      message: this.translate.instant('games.removeMessage', { name: game.name }),
      confirmLabel: this.translate.instant('games.remove'),
      destructive: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.gamesService.remove(this.groupId(), game.id)),
      )
      .subscribe(() => this.games.reload());
  }

  protected leave(): void {
    confirmAction(this.dialog, {
      title: this.translate.instant('groups.leave.title'),
      message: this.translate.instant('groups.leave.message'),
      confirmLabel: this.translate.instant('groups.leave.confirm'),
      destructive: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.groupsService.leave(this.groupId())),
      )
      .subscribe(() => void this.router.navigate(['/groups']));
  }

  private notify(key: string, params: Record<string, string>): void {
    this.snackBar.open(this.translate.instant(key, params), undefined, { duration: 3000 });
  }
}
