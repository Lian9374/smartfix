-- Additive reconciliation of the notification schemas published independently in V14/V20.
-- Historical migration files and checksums remain unchanged.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS dedup_key VARCHAR(200);
UPDATE notifications SET dedup_key = 'LEGACY_NOTIFICATION:' || id WHERE dedup_key IS NULL;
ALTER TABLE notifications ALTER COLUMN dedup_key SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_dedup_key ON notifications(dedup_key);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='notifications'::regclass AND conname='chk_notifications_event_type') THEN
        ALTER TABLE notifications ADD CONSTRAINT chk_notifications_event_type CHECK (char_length(event_type) BETWEEN 1 AND 80);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='notifications'::regclass AND conname='chk_notifications_title') THEN
        ALTER TABLE notifications ADD CONSTRAINT chk_notifications_title CHECK (char_length(title) BETWEEN 1 AND 150);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='notifications'::regclass AND conname='chk_notifications_message') THEN
        ALTER TABLE notifications ADD CONSTRAINT chk_notifications_message CHECK (char_length(message) BETWEEN 1 AND 1000);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='notifications'::regclass AND conname='chk_notifications_read_time') THEN
        ALTER TABLE notifications ADD CONSTRAINT chk_notifications_read_time CHECK (read_at IS NULL OR read_at >= created_at);
    END IF;
END $$;
