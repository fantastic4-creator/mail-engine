-- max_sends_per_hour = 0 means unlimited (inherits global config)
ALTER TABLE campaign ADD COLUMN max_sends_per_hour INTEGER NOT NULL DEFAULT 0;
