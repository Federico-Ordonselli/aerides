-- Weekly recurrence is stored as N concrete events (sign-ups, reminders and stats all need real rows).
-- The occurrences of one series share this id; recurrence_rule keeps the rule for display only.
ALTER TABLE events ADD COLUMN series_id UUID;
CREATE INDEX idx_events_series_id ON events (series_id) WHERE series_id IS NOT NULL;
