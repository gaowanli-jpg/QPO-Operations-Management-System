-- Data center direct database read-only role for order and part tracing.

SET search_path TO "${flyway:defaultSchema}";

DO $$
DECLARE
    ro_password text := '${datacenter_ro_password}';
BEGIN
    IF ro_password IS NULL OR btrim(ro_password) = '' THEN
        RAISE EXCEPTION 'POSTGRES_DATACENTER_RO_PASSWORD must be set before running this migration';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qpo_datacenter_ro') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', 'qpo_datacenter_ro', ro_password);
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', 'qpo_datacenter_ro', ro_password);
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), 'qpo_datacenter_ro');
END $$;

GRANT USAGE ON SCHEMA public TO qpo_datacenter_ro;
GRANT pg_read_all_data TO qpo_datacenter_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO qpo_datacenter_ro;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO qpo_datacenter_ro;

ALTER ROLE qpo_datacenter_ro SET search_path = public;
ALTER ROLE qpo_datacenter_ro SET default_transaction_read_only = on;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO qpo_datacenter_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO qpo_datacenter_ro;
