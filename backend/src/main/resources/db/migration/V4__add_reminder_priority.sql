ALTER TABLE reminders
    ADD COLUMN priority VARCHAR(20);

UPDATE reminders
SET priority = 'NONE'
WHERE priority IS NULL;

ALTER TABLE reminders
    ALTER COLUMN priority SET DEFAULT 'NONE';

ALTER TABLE reminders
    ALTER COLUMN priority SET NOT NULL;

ALTER TABLE reminders
    ADD CONSTRAINT chk_reminders_priority
    CHECK (priority IN ('NONE', 'LOW', 'MEDIUM', 'HIGH'));
