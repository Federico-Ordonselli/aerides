import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { CreateGroupDialogComponent } from './create-group-dialog.component';
import { GroupDetail } from './group.models';
import { GroupsService } from './groups.service';
import { JoinGroupDialogComponent } from './join-group-dialog.component';

@Component({
  selector: 'app-group-list',
  imports: [RouterLink, TranslatePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, EmptyStateComponent, ErrorStateComponent],
  templateUrl: './group-list.component.html',
  styleUrl: './group-list.component.scss',
})
export class GroupListComponent {
  private readonly groupsService = inject(GroupsService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly groups = rxResource({ stream: () => this.groupsService.listMine() });

  protected openCreate(): void {
    this.openAndGoToGroup(this.dialog.open<CreateGroupDialogComponent, void, GroupDetail>(CreateGroupDialogComponent));
  }

  protected openJoin(): void {
    this.openAndGoToGroup(this.dialog.open<JoinGroupDialogComponent, void, GroupDetail>(JoinGroupDialogComponent));
  }

  /** Both dialogs close with the group the user created or joined (or nothing if cancelled). */
  private openAndGoToGroup(dialogRef: MatDialogRef<unknown, GroupDetail>): void {
    dialogRef.afterClosed().subscribe((group) => {
      if (group) {
        void this.router.navigate(['/groups', group.id]);
      }
    });
  }
}
