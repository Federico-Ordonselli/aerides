-- Failed emails are retried a few times (FailedNotificationRetrier), and given up when no longer useful.
-- attempts counts every delivery attempt, the first one included.
ALTER TABLE notifications ADD COLUMN attempts INTEGER NOT NULL DEFAULT 1;

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_status;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'ABANDONED'));

-- The retry job looks only at failed rows: a partial index keeps that lookup small as the table grows
CREATE INDEX idx_notifications_failed ON notifications (id) WHERE status = 'FAILED';
