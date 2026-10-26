import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

import { GroupDetail } from './group.models';
import { GroupsService } from './groups.service';

/** Creates the group itself and closes with the result, so the caller only has to navigate. */
@Component({
  selector: 'app-create-group-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ 'groups.create.title' | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="dialog-form">
        @if (failed()) {
          <div class="form-alert" role="alert">{{ 'errors.generic' | translate }}</div>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'groups.fields.name' | translate }}</mat-label>
          <input matInput formControlName="name" cdkFocusInitial data-testid="group-name-input" />
          <mat-hint align="end">{{ form.controls.name.value.length }} / 80</mat-hint>
          <mat-error>{{ 'validation.length' | translate: { min: 1, max: 80 } }}</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'groups.fields.description' | translate }}</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
          <mat-error>{{ 'validation.maxLength' | translate: { max: 1000 } }}</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
        <button mat-flat-button type="submit" [disabled]="submitting()">{{ 'groups.create.submit' | translate }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .dialog-form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: min(28rem, 80vw);
    }
  `,
})
export class CreateGroupDialogComponent {
  private readonly groupsService = inject(GroupsService);
  private readonly dialogRef = inject<MatDialogRef<CreateGroupDialogComponent, GroupDetail>>(MatDialogRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    description: ['', Validators.maxLength(1000)],
  });
  protected readonly submitting = signal(false);
  protected readonly failed = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { name, description } = this.form.getRawValue();
    this.submitting.set(true);
    this.failed.set(false);
    this.groupsService.create({ name: name.trim(), description: description.trim() || null }).subscribe({
      next: (group) => this.dialogRef.close(group),
      error: () => {
        this.submitting.set(false);
        this.failed.set(true);
      },
    });
  }
}
