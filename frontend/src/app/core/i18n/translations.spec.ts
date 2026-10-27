import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

import { AUDIT_ACTIONS } from '../../features/audit/audit.models';
import { EVENT_STATUSES } from '../../features/events/event.models';

/**
 * Checks the translation files against each other and against the code, so a missing key is caught
 * by the test suite instead of showing up as "stats.title" on screen.
 */
const I18N_DIR = join(__dirname, '../../../../public/i18n');
const APP_DIR = join(__dirname, '../..');
const TOP_LEVEL_KEYS = ['common', 'auth', 'validation', 'errors', 'realtime', 'layout', 'home', 'groups', 'games', 'events', 'stats', 'audit', 'paginator'];

interface Translations {
  [key: string]: string | Translations;
}

function load(language: string): Translations {
  return JSON.parse(readFileSync(join(I18N_DIR, `${language}.json`), 'utf8')) as Translations;
}

/** {"a": {"b": "x"}} → ["a.b"] */
function flatten(translations: Translations, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) =>
    typeof value === 'string' ? [prefix + key] : flatten(value, `${prefix}${key}.`),
  );
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return sourceFiles(path);
    }
    return /\.(ts|html)$/.test(name) && !name.endsWith('.spec.ts') ? [path] : [];
  });
}

/** Every string literal in the app that looks like a translation key, e.g. 'groups.list.title'. */
function keysUsedInCode(): Set<string> {
  const pattern = new RegExp(`'((?:${TOP_LEVEL_KEYS.join('|')})(?:\\.[A-Za-z0-9]+)+)'`, 'g');
  const keys = new Set<string>();
  for (const file of sourceFiles(APP_DIR)) {
    for (const match of readFileSync(file, 'utf8').matchAll(pattern)) {
      keys.add(match[1]);
    }
  }
  return keys;
}

describe('translations', () => {
  const italian = new Set(flatten(load('it')));
  const english = new Set(flatten(load('en')));

  it('have the same keys in Italian and English', () => {
    expect([...italian].filter((key) => !english.has(key))).toEqual([]);
    expect([...english].filter((key) => !italian.has(key))).toEqual([]);
  });

  it('contain every key written in the code', () => {
    const used = keysUsedInCode();

    expect(used.size).toBeGreaterThan(100); // the scan itself works
    expect([...used].filter((key) => !english.has(key))).toEqual([]);
  });

  it('contain the keys built at runtime from enum values', () => {
    const dynamicKeys = [
      ...AUDIT_ACTIONS.map((action) => `audit.actions.${action}`),
      ...EVENT_STATUSES.map((status) => `events.status.${status}`),
    ];

    expect(dynamicKeys.filter((key) => !english.has(key))).toEqual([]);
  });
});
