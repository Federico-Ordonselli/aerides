// Wakes up the Render demo and opens it when it's ready.
// Any request to a sleeping free instance makes Render start it; this page keeps asking until the app answers.
'use strict';

const APP_URL = 'https://aerides.onrender.com/';
// Readiness answers only {"status":"UP"}, and the backend allows this one path cross-origin from GitHub Pages
const PROBE_URL = APP_URL + 'actuator/health/readiness';
const POLL_EVERY_MS = 5000;
const REQUEST_TIMEOUT_MS = 8000;
const GIVE_UP_AFTER_MS = 4 * 60 * 1000;
const REDIRECT_AFTER_MS = 3000;
// Where the progress bar is full: the usual wake-up time. It stops just short, so it never looks done before it is
const EXPECTED_SECONDS = 120;

const TEXTS = {
  en: {
    tagline: 'Named after the Tower of the Winds in Athens: the place to know when to gather.',
    statusTitle: 'Demo status',
    waking: 'Waking up the demo, it usually takes about 2 minutes.',
    up: 'The demo is ready.',
    redirecting: 'The demo is ready. Opening it in a moment…',
    slow: 'The demo is taking longer than usual. The free instance is sometimes slow to start: try again, or look at the code meanwhile.',
    elapsed: 'Elapsed',
    open: 'Open the demo',
    stay: 'Stay on this page',
    retry: 'Try again',
    why: 'The demo runs on a free instance that sleeps after 15 idle minutes. This page wakes it up and opens it when it\'s ready.',
    aboutTitle: 'What it is',
    about1: 'Aerides is a multi-tenant web app where gaming groups (guilds, clans, tabletop tables) organize their sessions: events with role-based slots, sign-ups, approvals and automatic waitlists.',
    about2: 'When a seat frees up, the first person on the waitlist is promoted and sees it live, without reloading. Groups get email and Discord reminders, an audit log and attendance statistics.',
    about3: 'Built with Java 21, Spring Boot 4, PostgreSQL and Angular, tested with Testcontainers and Playwright, shipped with Docker and GitHub Actions.',
    tryTitle: 'Try it',
    login: 'Log in as',
    owner: '(owner of a group).',
    accounts: 'All six demo accounts and what each one is for',
    repo: 'Source code on GitHub',
    direct: 'Open the app directly',
    shotsTitle: 'Screenshots',
    raidCaption: 'A full raid with its waitlist',
    raidAlt: 'A full raid: Tank, Healer and DPS seats taken, and one person on the Tank waitlist',
    eventsCaption: 'The events of a group',
    eventsAlt: 'The events of a group in a table, with date, title, state and seats taken',
    statsCaption: 'Group statistics',
    statsAlt: 'Group statistics: events per month, the most unfilled roles and attendance per member',
    pageTitle: 'Aerides: waking up the demo',
    langGroup: 'Language',
  },
  it: {
    tagline: 'Prende il nome dalla Torre dei Venti di Atene: il posto dove sapere quando ritrovarsi.',
    statusTitle: 'Stato della demo',
    waking: 'Sto svegliando la demo, di solito ci vogliono circa 2 minuti.',
    up: 'La demo è pronta.',
    redirecting: 'La demo è pronta. La apro tra un attimo…',
    slow: 'La demo ci sta mettendo più del solito. L\'istanza gratuita a volte è lenta a partire: riprova, o guarda il codice nel frattempo.',
    elapsed: 'Tempo trascorso',
    open: 'Apri la demo',
    stay: 'Resta su questa pagina',
    retry: 'Riprova',
    why: 'La demo gira su un\'istanza gratuita che si addormenta dopo 15 minuti senza visite. Questa pagina la sveglia e la apre quando è pronta.',
    aboutTitle: 'Che cos\'è',
    about1: 'Aerides è una web app multi-tenant in cui i gruppi di gioco (gilde, clan, tavoli di gioco di ruolo) organizzano le sessioni: eventi con posti per ruolo, iscrizioni, approvazioni e liste d\'attesa automatiche.',
    about2: 'Quando si libera un posto, il primo in lista d\'attesa viene promosso e lo vede subito, senza ricaricare la pagina. I gruppi hanno promemoria via email e Discord, un registro attività e statistiche sulle presenze.',
    about3: 'Realizzata con Java 21, Spring Boot 4, PostgreSQL e Angular, testata con Testcontainers e Playwright, distribuita con Docker e GitHub Actions.',
    tryTitle: 'Provala',
    login: 'Accedi come',
    owner: '(proprietaria di un gruppo).',
    accounts: 'Tutti e sei gli account demo e a cosa serve ciascuno',
    repo: 'Codice sorgente su GitHub',
    direct: 'Apri direttamente l\'app',
    shotsTitle: 'Schermate',
    raidCaption: 'Un raid al completo con la sua lista d\'attesa',
    raidAlt: 'Un raid al completo: posti Tank, Healer e DPS occupati e una persona in lista d\'attesa come Tank',
    eventsCaption: 'Gli eventi di un gruppo',
    eventsAlt: 'Gli eventi di un gruppo in una tabella, con data, titolo, stato e posti occupati',
    statsCaption: 'Statistiche del gruppo',
    statsAlt: 'Statistiche del gruppo: eventi al mese, ruoli più scoperti e presenze per membro',
    pageTitle: 'Aerides: sveglio la demo',
    langGroup: 'Lingua',
  },
};

const $ = (id) => document.getElementById(id);

// --- Language: the browser's first choice between Italian and English, or the one picked with the toggle ---

function storedLanguage() {
  try {
    return localStorage.getItem('lang');
  } catch {
    return null; // storage blocked (private mode, disabled cookies): just don't remember the choice
  }
}

function browserLanguage() {
  const first = (navigator.languages || [navigator.language || 'en'])
    .map((tag) => tag.toLowerCase().slice(0, 2))
    .find((code) => code in TEXTS);
  return first || 'en';
}

let lang = TEXTS[storedLanguage()] ? storedLanguage() : browserLanguage();

function applyLanguage() {
  const t = TEXTS[lang];
  document.documentElement.lang = lang;
  document.title = t.pageTitle;
  document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t[el.dataset.i18n]; });
  document.querySelectorAll('[data-i18n-alt]').forEach((el) => { el.alt = t[el.dataset.i18nAlt]; });
  document.querySelector('.lang').setAttribute('aria-label', t.langGroup);
  document.querySelectorAll('.lang button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.lang === lang)));
  $('message').textContent = t[state];
}

document.querySelectorAll('.lang button').forEach((button) => {
  button.addEventListener('click', () => {
    lang = button.dataset.lang;
    try {
      localStorage.setItem('lang', lang);
    } catch {
      // see storedLanguage()
    }
    applyLanguage();
  });
});

// --- Wake-up ---

// One of the keys of TEXTS: waking, redirecting, up, slow
let state = 'waking';
// Each attempt (the first one, or after "Try again") has its own number, so timers of an old attempt do nothing
let attempt = 0;
let startedAt = 0;
let clock = 0;
let redirectTimer = 0;

/**
 * True only when the app itself says it is ready.
 * Measured on Render (2026-10-27): while the instance starts, this fetch (default Accept header) hangs with no answer, and
 * the app answered after about 130 s. A browser navigation (Accept: text/html) instead gets Render's
 * "Application loading" HTML page at once, with status 503. None of these counts as "up":
 * - a request that hangs: the per-request timeout ends it, and we ask again
 * - Render's HTML loading page, or a 502/503: no CORS header, so the browser doesn't even let us read it
 *   (fetch rejects); and even if it did, it isn't JSON with status UP
 * - readiness DOWN/OUT_OF_SERVICE while the app is still starting: a 503 with a JSON body
 */
async function isUp() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(PROBE_URL, { cache: 'no-store', signal: controller.signal });
    if (!response.ok || !(response.headers.get('content-type') || '').includes('json')) {
      return false;
    }
    const body = await response.json();
    return body?.status === 'UP';
  } catch {
    return false; // network error, CORS block (an answer from Render, not from the app), timeout
  } finally {
    clearTimeout(timeout);
  }
}

function show(newState) {
  state = newState;
  $('message').textContent = TEXTS[lang][state];
  $('message').dataset.state = state;
  const ready = state === 'up' || state === 'redirecting';
  $('open').hidden = !ready;
  $('stay').hidden = state !== 'redirecting';
  $('retry').hidden = state !== 'slow';
}

function tick() {
  const seconds = Math.floor((Date.now() - startedAt) / 1000);
  $('elapsed').textContent = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  if (state === 'waking') {
    $('progress').value = Math.min(seconds, EXPECTED_SECONDS * 0.95);
  }
}

async function poll(current) {
  const askedAt = Date.now();
  const up = await isUp();
  if (current !== attempt) {
    return;
  }
  if (up) {
    clearInterval(clock);
    $('progress').value = EXPECTED_SECONDS;
    show('redirecting');
    redirectTimer = setTimeout(() => window.location.assign(APP_URL), REDIRECT_AFTER_MS);
    return;
  }
  if (Date.now() - startedAt >= GIVE_UP_AFTER_MS) {
    clearInterval(clock);
    show('slow');
    return;
  }
  // Every POLL_EVERY_MS from the start of the previous request, never two requests at once
  setTimeout(() => poll(current), Math.max(0, POLL_EVERY_MS - (Date.now() - askedAt)));
}

function start() {
  attempt += 1;
  startedAt = Date.now();
  $('progress').value = 0;
  show('waking');
  tick();
  clearInterval(clock);
  clock = setInterval(tick, 1000);
  poll(attempt);
}

$('stay').addEventListener('click', () => {
  clearTimeout(redirectTimer);
  show('up');
  $('open').focus();
});

$('retry').addEventListener('click', start);

applyLanguage();
start();
