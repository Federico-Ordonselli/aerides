import { computed, inject, Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export const SUPPORTED_LANGUAGES = ['it', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

const STORAGE_KEY = 'aerides.language';

/** Chooses the UI language (saved choice > browser language > Italian) and remembers changes. */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);

  readonly current = computed<Language>(() => {
    const language = this.translate.currentLang();
    return isSupported(language) ? language : 'it';
  });

  init(): void {
    this.translate.use(this.initialLanguage());
  }

  use(language: Language): void {
    this.translate.use(language);
    localStorage.setItem(STORAGE_KEY, language);
  }

  private initialLanguage(): Language {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (isSupported(saved)) {
      return saved;
    }
    return navigator.language.toLowerCase().startsWith('en') ? 'en' : 'it';
  }
}

function isSupported(value: string | null): value is Language {
  return SUPPORTED_LANGUAGES.includes(value as Language);
}
