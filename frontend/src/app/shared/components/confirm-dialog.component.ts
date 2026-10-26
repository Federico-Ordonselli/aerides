import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable } from 'rxjs';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  /** Red confirm button, for destructive actions. */
  destructive?: boolean;
}

/** Generic "Are you sure?" dialog. Use `confirmAction(dialog, data)` instead of opening it directly. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, TranslatePipe],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [class.destructive]="data.destructive" [mat-dialog-close]="true" cdkFocusInitial>
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .destructive {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}

/** Opens the confirm dialog; emits true only if the user confirmed. */
export function confirmAction(dialog: MatDialog, data: ConfirmDialogData): Observable<boolean | undefined> {
  return dialog.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, { data }).afterClosed();
}
