ALTER TABLE reminder_lists
    ADD COLUMN user_id BIGINT,
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_reminder_lists_user FOREIGN KEY (user_id) REFERENCES users (id),
    ADD CONSTRAINT ck_reminder_lists_default_owner CHECK (NOT is_default OR user_id IS NOT NULL);

CREATE INDEX idx_reminder_lists_user_id ON reminder_lists (user_id);
CREATE INDEX idx_reminders_list_id ON reminders (list_id);

CREATE UNIQUE INDEX ux_reminder_lists_default_user
    ON reminder_lists (user_id)
    WHERE is_default;

-- Give existing V8A users their own defaults while preserving all legacy lists.
INSERT INTO reminder_lists (name, created_at, user_id, is_default)
SELECT 'Reminders', CURRENT_TIMESTAMP, id, TRUE
FROM users;
