-- Raidboard initial schema.
-- Enum-like columns are stored as VARCHAR with CHECK constraints: they map to @Enumerated(STRING)
-- in JPA and stay readable in SQL queries, while the database still rejects invalid values.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    avatar_url    VARCHAR(500),
    locale        VARCHAR(5)   NOT NULL DEFAULT 'it',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the raw token, hex encoded: the raw value is never stored
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE groups (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(80)  NOT NULL,
    slug                VARCHAR(80)  NOT NULL,
    description         VARCHAR(1000),
    invite_code         VARCHAR(16)  NOT NULL,
    discord_webhook_url VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_groups_slug UNIQUE (slug),
    CONSTRAINT uk_groups_invite_code UNIQUE (invite_code)
);

CREATE TABLE memberships (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id  BIGINT      NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    role      VARCHAR(10) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_memberships_user_group UNIQUE (user_id, group_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'OFFICER', 'MEMBER'))
);
CREATE INDEX idx_memberships_group ON memberships (group_id);

CREATE TABLE games (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id  BIGINT       NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    rawg_id   BIGINT       NOT NULL,
    name      VARCHAR(200) NOT NULL,
    slug      VARCHAR(200) NOT NULL,
    cover_url VARCHAR(500),
    CONSTRAINT uk_games_group_rawg UNIQUE (group_id, rawg_id)
);

CREATE TABLE events (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id                BIGINT       NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    game_id                 BIGINT       REFERENCES games (id) ON DELETE SET NULL,
    title                   VARCHAR(120) NOT NULL,
    description             VARCHAR(2000),
    starts_at               TIMESTAMPTZ  NOT NULL,
    duration_minutes        INTEGER      NOT NULL,
    status                  VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    requires_approval       BOOLEAN      NOT NULL DEFAULT FALSE,
    withdraw_deadline_hours INTEGER      NOT NULL DEFAULT 0,
    recurrence_rule         VARCHAR(100),
    created_by              BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- optimistic locking (@Version): concurrent edits of the same event fail instead of overwriting each other
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_events_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_events_withdraw_deadline CHECK (withdraw_deadline_hours >= 0)
);
CREATE INDEX idx_events_group_starts_at ON events (group_id, starts_at);

CREATE TABLE event_slots (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id  BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    role_name VARCHAR(40) NOT NULL,
    capacity  INTEGER     NOT NULL,
    CONSTRAINT uk_event_slots_event_role UNIQUE (event_id, role_name),
    CONSTRAINT ck_event_slots_capacity CHECK (capacity > 0)
);

CREATE TABLE signups (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    slot_id    BIGINT      NOT NULL REFERENCES event_slots (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL,
    -- FIFO order inside a slot, used to pick who is promoted from the waitlist
    position   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_signups_status CHECK (status IN ('CONFIRMED', 'WAITLISTED', 'PENDING', 'REJECTED', 'WITHDRAWN'))
);
-- "At most one active signup per user per event", enforced by the database itself.
-- It is a partial index: withdrawn or rejected signups don't count, so a user can sign up again.
CREATE UNIQUE INDEX uk_signups_active_user_event ON signups (event_id, user_id)
    WHERE status IN ('CONFIRMED', 'WAITLISTED', 'PENDING');
CREATE INDEX idx_signups_slot_status_position ON signups (slot_id, status, position);

CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40) NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    status     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    -- e.g. 'reminder:event:42': makes "send the reminder exactly once per user per event" a DB guarantee
    dedup_key  VARCHAR(100),
    sent_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_notifications_user_dedup UNIQUE (user_id, dedup_key),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE TABLE audit_entries (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id    BIGINT      REFERENCES groups (id) ON DELETE CASCADE,
    actor_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    action      VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT,
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entries_group_created_at ON audit_entries (group_id, created_at DESC);
