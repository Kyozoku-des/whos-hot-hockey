-- When the data job last finished a sync of the current season, shown on the site as
-- "Stats last updated". One row per kind of sync; written by the data job, read by the API.

CREATE TABLE sync_status (
    sync_name VARCHAR(64) PRIMARY KEY,
    last_success_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
