import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { passwordsMatch } from '../../shared/validators/passwords-match.validator';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, ButtonModule, CardModule, InputTextModule, MessageModule, PasswordModule],
  templateUrl: './register.component.html',
  styleUrl: './auth-page.scss',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly languageService = inject(LanguageService);
  private readonly router = inject(Router);

  // Same limits as the backend RegisterRequest, so most errors are caught before the request is sent
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch('password', 'confirmPassword') },
  );
  protected readonly submitting = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { displayName, email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorKey.set(null);
    this.authService
      .register({ displayName, email, password, locale: this.languageService.current() })
      .subscribe({
        next: () => void this.router.navigateByUrl('/'),
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorKey.set(error.status === 409 ? 'auth.register.emailTaken' : 'errors.generic');
        },
      });
  }
}
