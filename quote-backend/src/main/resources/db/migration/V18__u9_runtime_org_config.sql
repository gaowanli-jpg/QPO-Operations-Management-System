CREATE TABLE IF NOT EXISTS u9_runtime_config (
    id integer PRIMARY KEY,
    org_code varchar(40) NOT NULL,
    updated_at timestamp without time zone NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_u9_runtime_config_singleton CHECK (id = 1),
    CONSTRAINT ck_u9_runtime_config_org_code_not_blank CHECK (btrim(org_code) <> '')
);

INSERT INTO u9_runtime_config (id, org_code, updated_at)
SELECT 1,
       COALESCE(NULLIF(btrim(active_u9.config_json ->> 'orgCode'), ''), '101'),
       NOW()
FROM (
    SELECT config_json
    FROM system_external_integration_config
    WHERE system_code = 'U9'
      AND active = TRUE
      AND enabled = TRUE
    ORDER BY id
    LIMIT 1
) active_u9
ON CONFLICT (id) DO NOTHING;

INSERT INTO u9_runtime_config (id, org_code, updated_at)
SELECT 1, '101', NOW()
WHERE NOT EXISTS (SELECT 1 FROM u9_runtime_config WHERE id = 1);
