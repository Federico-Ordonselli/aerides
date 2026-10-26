# Raidboard

Piattaforma web per organizzare sessioni di gioco in gruppo (gilde, clan, tavoli di D&D): eventi, iscrizioni per ruolo, liste d'attesa automatiche, promemoria e statistiche.

Sotto la superficie è un **gestionale di prenotazione risorse multi-tenant** con ruoli, approvazioni, notifiche, audit e report: lo stesso tipo di software che le consulenze IT sviluppano per banche, assicurazioni e PA. Obiettivo del progetto: dimostrare in un repository verificabile le competenze Java/Spring Boot + Angular più richieste negli annunci junior full-stack in Italia.

---

## 1. Funzionalità

### MVP
- **Account e gruppi**
  - Registrazione, login, logout, refresh del token
  - Creazione di un gruppo; inviti tramite codice
  - Ruoli per gruppo: **Owner**, **Officer**, **Member**
- **Catalogo giochi**
  - Ricerca giochi dall'API pubblica RAWG (rawg.io) e aggiunta al gruppo
  - Copia locale di nome, copertina e slug, per non dipendere dall'API a ogni richiesta
- **Eventi**
  - Titolo, gioco, data e ora, durata, descrizione, requisiti (es. livello minimo)
  - Posti per ruolo (es. 2 tank, 2 healer, 6 DPS; oppure 1 master + 5 giocatori)
  - Eventi ricorrenti (settimanali)
  - Stati: bozza, pubblicato, annullato, concluso
- **Iscrizioni**
  - Iscrizione scegliendo un ruolo
  - Ruolo pieno → lista d'attesa (ordine FIFO)
  - Quando qualcuno si ritira, il primo in attesa viene promosso automaticamente
  - Approvazione manuale opzionale da parte di un Officer
  - Ritiro consentito fino a X ore prima dell'evento
- **Tempo reale**
  - La lista iscritti di un evento si aggiorna in diretta via WebSocket
- **Notifiche**
  - Email di promemoria 24 ore prima dell'evento
  - Email di promozione dalla lista d'attesa
  - Webhook Discord opzionale per gruppo (nuovo evento, evento quasi pieno)
- **Dashboard**
  - Presenze per membro, eventi per mese, ruoli più spesso scoperti
- **Audit log**
  - Chi ha creato, modificato o annullato cosa, e quando

### Extra (se avanza tempo)
- Notifiche asincrone con RabbitMQ
- Cache Redis per il catalogo giochi
- Ruolo admin globale con statistiche di sistema
- PWA installabile
- Export CSV delle presenze

---

## 2. Stack tecnico

### Backend
| Tecnologia | Uso |
|---|---|
| Java 21, Spring Boot 3 | Base dell'applicazione |
| Spring Web | API REST |
| Spring Security + JWT (access + refresh token) | Autenticazione; autorizzazione per gruppo con `@PreAuthorize` |
| Spring Data JPA / Hibernate | Persistenza |
| PostgreSQL | Database |
| Flyway | Migrazioni dello schema |
| Bean Validation | Validazione degli input |
| MapStruct | Mapping DTO ↔ entità |
| `ProblemDetail` (RFC 9457) | Formato standard degli errori |
| Specification / query custom | Filtri dinamici e paginazione |
| springdoc-openapi | Documentazione Swagger UI |
| Spring WebSocket (STOMP) | Aggiornamenti live delle iscrizioni |
| `@Scheduled` + Spring Mail | Promemoria e email |
| Spring Boot Actuator | Health check e metriche |
| JUnit 5, Mockito, Testcontainers | Test unitari e di integrazione |

### Frontend
| Tecnologia | Uso |
|---|---|
| Angular (ultima versione stabile) | Standalone components, signals, nuovo control flow (`@if`, `@for`) |
| Reactive forms | Form con validatori custom |
| HTTP interceptor | Allegare il JWT, rinnovare il token, gestire gli errori |
| Route guard | Accesso per ruolo |
| Lazy loading | Caricamento delle sezioni su richiesta |
| RxJS | Ricerca con debounce, stream WebSocket |
| PrimeNG | Tabelle, calendario, dialog, grafici |
| i18n | Interfaccia in italiano e inglese |
| Jest + Playwright | Test unitari ed end-to-end |

### DevOps
- Docker e Docker Compose (backend, frontend, PostgreSQL, Mailpit)
- GitHub Actions: build, test, immagine Docker a ogni push
- Deploy su Azure (Container Apps + Azure Database for PostgreSQL) con Terraform; in alternativa Render o Fly.io
- README con screenshot, diagramma dell'architettura, credenziali demo e link a Swagger

---

## 3. Modello dati

| Entità | Campi principali |
|---|---|
| **User** | id, email (unica), passwordHash, displayName, avatarUrl, locale, createdAt |
| **Group** | id, name, slug, description, inviteCode, discordWebhookUrl, createdAt |
| **Membership** | id, user, group, role (OWNER / OFFICER / MEMBER), joinedAt — vincolo unico (user, group) |
| **Game** | id, group, rawgId, name, slug, coverUrl |
| **Event** | id, group, game, title, description, startsAt, durationMinutes, status, requiresApproval, withdrawDeadlineHours, recurrenceRule, createdBy, version (optimistic locking) |
| **EventSlot** | id, event, roleName, capacity |
| **Signup** | id, event, slot, user, status (CONFIRMED / WAITLISTED / PENDING / REJECTED / WITHDRAWN), position, createdAt — vincolo: un'iscrizione attiva per utente per evento |
| **Notification** | id, user, type, payload, sentAt, status |
| **AuditEntry** | id, group, actor, action, entityType, entityId, details (JSON), createdAt |
| **RefreshToken** | id, user, tokenHash, expiresAt, revoked |

**Regole di business chiave**
- Le iscrizioni a uno stesso posto devono essere sicure in caso di richieste contemporanee (optimistic locking o lock pessimistico sulla riga dello slot).
- La promozione dalla lista d'attesa avviene nella stessa transazione del ritiro.
- Solo Owner e Officer creano, modificano e annullano eventi; solo l'Owner gestisce i ruoli.

---

## 4. Struttura del repository

```
raidboard/
├── backend/
│   └── src/main/java/dev/ordonselli/raidboard/
│       ├── auth/          (login, JWT, refresh token, sicurezza)
│       ├── user/
│       ├── group/         (gruppi, membership, inviti)
│       ├── game/          (catalogo + client RAWG)
│       ├── event/         (eventi, slot, ricorrenze)
│       ├── signup/        (iscrizioni, lista d'attesa)
│       ├── notification/  (email, Discord, scheduler)
│       ├── realtime/      (WebSocket)
│       ├── stats/         (dashboard)
│       ├── audit/
│       └── common/        (errori, config, utilità)
├── frontend/
│   └── src/app/
│       ├── core/          (auth, interceptor, guard, layout)
│       ├── shared/        (componenti e pipe riusabili)
│       └── features/      (groups, games, events, dashboard, profile)
├── infra/                 (Terraform)
├── docker-compose.yml
├── .github/workflows/
└── README.md
```

Il codice è organizzato **per funzionalità**, non per livello tecnico: ogni package contiene controller, service, repository, DTO e mapper del proprio dominio.

---

## 5. Tabella di marcia (circa 6 settimane)

| Settimana | Obiettivo | Fatto quando… |
|---|---|---|
| 1 | Setup repository, Docker Compose, entità e migrazioni Flyway, autenticazione JWT, struttura Angular con login e registrazione | Ti registri, fai login e vedi una pagina protetta |
| 2 | Gruppi, membership, inviti, ruoli e permessi; catalogo giochi con RAWG | Crei un gruppo, inviti un secondo utente, aggiungi giochi |
| 3 | Eventi, slot, iscrizioni, lista d'attesa, approvazioni; test della logica di iscrizione | La promozione automatica funziona ed è coperta da test |
| 4 | WebSocket live, promemoria schedulati, email (Mailpit), webhook Discord | Due browser aperti vedono le iscrizioni aggiornarsi da sole |
| 5 | Dashboard, audit log, filtri e paginazione, i18n, rifinitura interfaccia | L'app è presentabile anche da telefono |
| 6 | Testcontainers, Playwright, CI/CD, deploy, README, video demo | Link pubblico funzionante e pipeline verde |

---

## 6. Come presentarlo

**Nel CV**
> **Raidboard** – piattaforma multi-tenant di pianificazione eventi. Java 21, Spring Boot 3, Spring Security/JWT, JPA/Hibernate, PostgreSQL, Flyway, Angular, PrimeNG, WebSocket, Docker, GitHub Actions, Azure. Gestione ruoli e permessi, liste d'attesa con promozione automatica, notifiche schedulate, test di integrazione con Testcontainers.

**Ai colloqui, punti da saper spiegare**
- Come funziona il flusso JWT con refresh token e dove salvi i token
- Come eviti che due persone prendano l'ultimo posto nello stesso momento
- Perché hai organizzato il codice per funzionalità
- Cosa testi con Mockito e cosa con Testcontainers, e perché
- Come funziona la pipeline CI/CD e il deploy

**Nel README**
- GIF o screenshot delle schermate principali
- Diagramma dell'architettura
- Avvio locale con un solo comando (`docker compose up`)
- Utente demo già pronto
- Link a demo online e Swagger
