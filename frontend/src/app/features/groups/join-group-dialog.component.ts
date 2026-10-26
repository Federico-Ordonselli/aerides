import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

import { GroupDetail } from './group.models';
import { GroupsService } from './groups.service';

@Component({
  selector: 'app-join-group-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ 'groups.join.title' | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="dialog-form">
        <p>{{ 'groups.join.help' | translate }}</p>
        @if (errorKey(); as key) {
          <div class="form-alert" role="alert">{{ key | translate }}</div>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'groups.fields.inviteCode' | translate }}</mat-label>
          <input matInput formControlName="inviteCode" class="code-input" autocomplete="off" cdkFocusInitial />
          <mat-error>{{ 'validation.required' | translate }}</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
        <button mat-flat-button type="submit" [disabled]="submitting()">{{ 'groups.join.submit' | translate }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .dialog-form {
      min-width: min(24rem, 80vw);
    }
    .code-input {
      text-transform: uppercase;
      letter-spacing: 0.15em;
      font-family: monospace;
    }
  `,
})
export class JoinGroupDialogComponent {
  private readonly groupsService = inject(GroupsService);
  private readonly dialogRef = inject<MatDialogRef<JoinGroupDialogComponent, GroupDetail>>(MatDialogRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    inviteCode: ['', [Validators.required, Validators.maxLength(16)]],
  });
  protected readonly submitting = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorKey.set(null);
    this.groupsService.join(this.form.getRawValue().inviteCode.trim()).subscribe({
      next: (group) => this.dialogRef.close(group),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorKey.set(joinErrorKey(error.status));
      },
    });
  }
}

function joinErrorKey(status: number): string {
  if (status === 404) {
    return 'groups.join.invalidCode';
  }
  if (status === 409) {
    return 'groups.join.alreadyMember';
  }
  return 'errors.generic';
}
