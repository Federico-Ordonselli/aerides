import { Clipboard } from '@angular/cdk/clipboard';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';

import { confirmAction } from '../../shared/components/confirm-dialog.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { GroupsService } from './groups.service';

/** Same rule as the backend DiscordWebhookRequest: only real Discord webhook URLs. */
export const DISCORD_WEBHOOK_PATTERN = /^https:\/\/(discord\.com|discordapp\.com)\/api\/webhooks\/\d+\/[A-Za-z0-9_-]+$/;

/** Reached only by OFFICER and OWNER (groupRoleGuard); owner-only sections are hidden for officers. */
@Component({
  selector: 'app-group-settings',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
    ErrorStateComponent,
  ],
  templateUrl: './group-settings.component.html',
  styleUrl: './group-settings.component.scss',
})
export class GroupSettingsComponent {
  private readonly groupsService = inject(GroupsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly clipboard = inject(Clipboard);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly groupId = input.required({ transform: numberAttribute });

  protected readonly group = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.groupsService.get(params),
  });
  protected readonly isOwner = computed(() => this.group.hasValue() && this.group.value().myRole === 'OWNER');

  protected readonly detailsForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    description: ['', Validators.maxLength(1000)],
  });
  protected readonly webhookForm = this.formBuilder.group({
    url: ['', [Validators.maxLength(500), Validators.pattern(DISCORD_WEBHOOK_PATTERN)]],
  });
  protected readonly inviteCode = signal<string | null>(null);
  protected readonly saving = signal(false);

  constructor() {
    // Fill the forms when the group arrives (and again after a reload)
    effect(() => {
      if (!this.group.hasValue()) {
        return;
      }
      const group = this.group.value();
      this.detailsForm.reset({ name: group.name, description: group.description ?? '' });
      this.webhookForm.reset({ url: group.discordWebhookUrl ?? '' });
      this.inviteCode.set(group.inviteCode);
    });
  }

  protected saveDetails(): void {
    if (this.detailsForm.invalid) {
      this.detailsForm.markAllAsTouched();
      return;
    }
    const { name, description } = this.detailsForm.getRawValue();
    this.saving.set(true);
    this.groupsService.update(this.groupId(), { name: name.trim(), description: description.trim() || null }).subscribe({
      next: () => {
        this.saving.set(false);
        this.notify('groups.settings.saved');
        this.group.reload();
      },
      error: () => this.saving.set(false),
    });
  }

  protected copyInviteCode(): void {
    const code = this.inviteCode();
    if (code && this.clipboard.copy(code)) {
      this.notify('groups.settings.inviteCopied');
    }
  }

  protected regenerateInviteCode(): void {
    confirmAction(this.dialog, {
      title: this.translate.instant('groups.settings.regenerateTitle'),
      message: this.translate.instant('groups.settings.regenerateMessage'),
      confirmLabel: this.translate.instant('groups.settings.regenerate'),
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.groupsService.regenerateInviteCode(this.groupId())),
      )
      .subscribe((code) => {
        this.inviteCode.set(code);
        this.notify('groups.settings.inviteRegenerated');
      });
  }

  protected saveWebhook(): void {
    if (this.webhookForm.invalid) {
      this.webhookForm.markAllAsTouched();
      return;
    }
    const url = this.webhookForm.getRawValue().url.trim() || null;
    this.groupsService.updateDiscordWebhook(this.groupId(), url).subscribe(() => {
      this.notify(url ? 'groups.settings.webhookSaved' : 'groups.settings.webhookRemoved');
      this.group.reload();
    });
  }

  protected deleteGroup(): void {
    const name = this.group.hasValue() ? this.group.value().name : '';
    confirmAction(this.dialog, {
      title: this.translate.instant('groups.settings.deleteTitle'),
      message: this.translate.instant('groups.settings.deleteMessage', { name }),
      confirmLabel: this.translate.instant('groups.settings.delete'),
      destructive: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.groupsService.delete(this.groupId())),
      )
      .subscribe(() => {
        this.notify('groups.settings.deleted');
        void this.router.navigate(['/groups']);
      });
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), undefined, { duration: 3000 });
  }
}
