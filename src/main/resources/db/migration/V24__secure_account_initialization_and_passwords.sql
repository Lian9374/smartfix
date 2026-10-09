-- Existing credentials remain valid. Managed accounts are marked by the application.
ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

-- One lockable row prevents a second bootstrap, even if its configured username changes.
CREATE TABLE account_initialization (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    completed BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ
);
INSERT INTO account_initialization(id, completed, completed_at)
SELECT 1, EXISTS(SELECT 1 FROM users WHERE role = 'ADMINISTRATOR'),
    CASE WHEN EXISTS(SELECT 1 FROM users WHERE role = 'ADMINISTRATOR') THEN CURRENT_TIMESTAMP ELSE NULL END;

ALTER TABLE audit_entries DROP CONSTRAINT chk_audit_entries_target;
ALTER TABLE audit_entries ADD CONSTRAINT chk_audit_entries_target
    CHECK (target_type IN ('QUESTION','ANSWER','REPORT','USER'));
