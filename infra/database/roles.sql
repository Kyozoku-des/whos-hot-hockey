-- Run after Flyway, against both fresh and existing databases. Never echo secrets.
\set ON_ERROR_STOP on
\getenv api_password API_DB_PASSWORD
\getenv ingest_password INGEST_DB_PASSWORD
\getenv admin_password PGPASSWORD
\getenv profile APP_PROFILE

-- Disable statement capture before even validating interpolated passwords.
SET log_statement = 'none';
SET log_min_duration_statement = -1;
SET log_min_duration_sample = -1;
SET log_min_error_statement = 'panic';

SELECT length(:'api_password') > 0 AND length(:'ingest_password') > 0
       AND :'api_password' <> :'ingest_password'
       AND :'api_password' <> :'admin_password' AND :'ingest_password' <> :'admin_password'
       AND (:'profile' <> 'prod' OR
            (length(:'api_password') >= 20 AND length(:'ingest_password') >= 20
             AND :'api_password' NOT IN ('whoshot_local', 'whoshot_api_local', 'whoshot_ingest_local')
             AND :'ingest_password' NOT IN ('whoshot_local', 'whoshot_api_local', 'whoshot_ingest_local')
             AND :'admin_password' NOT IN ('whoshot_local', 'whoshot_api_local', 'whoshot_ingest_local'))) AS credentials_valid \gset
\if :credentials_valid
\else
    DO $$ BEGIN
        RAISE EXCEPTION 'Runtime database passwords must be distinct; production requires non-default passwords of at least 20 characters';
    END $$;
\endif

BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'whoshot_api') THEN
        CREATE ROLE whoshot_api LOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'whoshot_ingest') THEN
        CREATE ROLE whoshot_ingest LOGIN;
    END IF;
    -- These are dedicated service identities, not owners or members of other roles.
    IF EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member
               WHERE r.rolname IN ('whoshot_api', 'whoshot_ingest'))
       OR EXISTS (SELECT FROM pg_class c JOIN pg_roles r ON r.oid = c.relowner
                  WHERE r.rolname IN ('whoshot_api', 'whoshot_ingest')) THEN
        RAISE EXCEPTION 'Runtime database roles have unexpected ownership or membership; review them before retrying';
    END IF;
END $$;
ALTER ROLE whoshot_api NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;
ALTER ROLE whoshot_ingest NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;
-- format(%L) quotes even passwords containing SQL metacharacters. psql does not
-- echo these statements; do not enable -a, -e or server statement logging here.
SELECT format('ALTER ROLE whoshot_api PASSWORD %L', :'api_password') \gexec
SELECT format('ALTER ROLE whoshot_ingest PASSWORD %L', :'ingest_password') \gexec

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE TEMPORARY ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO whoshot_api, whoshot_ingest;
GRANT USAGE ON SCHEMA public TO whoshot_api, whoshot_ingest;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM whoshot_api, whoshot_ingest;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM whoshot_api, whoshot_ingest;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO whoshot_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO whoshot_ingest;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO whoshot_ingest;
REVOKE ALL ON public.flyway_schema_history FROM whoshot_api, whoshot_ingest;
-- Flyway connects as the existing whoshot owner. Future tables inherit the same
-- grants without requiring runtime services to own or migrate the schema.
ALTER DEFAULT PRIVILEGES FOR ROLE whoshot IN SCHEMA public GRANT SELECT ON TABLES TO whoshot_api;
ALTER DEFAULT PRIVILEGES FOR ROLE whoshot IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO whoshot_ingest;
ALTER DEFAULT PRIVILEGES FOR ROLE whoshot IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO whoshot_ingest;
ALTER ROLE whoshot_api SET default_transaction_read_only = on;
COMMIT;
