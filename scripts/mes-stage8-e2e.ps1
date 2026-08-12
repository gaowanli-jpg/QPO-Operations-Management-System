[CmdletBinding()]
param(
    [string]$BackendBaseUrl = "http://127.0.0.1:8080",
    [string]$PostgresContainer = "quote-postgres",
    [string]$DbUser = "postgres",
    [string]$DbName = "postgres",
    [string]$RunId,
    [string]$QpoBearerToken,
    [string]$DrawingUrl,
    [string]$MaterialNoP001,
    [string]$MaterialNoP002,
    [switch]$ExecuteExternalWrites,
    [switch]$SkipDataSetup,
    [int]$TaskPollSeconds = 45
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

$MaterialEndpointPath = "/api/v1/mes/openapi/material-preparation/create"
$ProductionEndpointPath = "/api/v1/mes/openapi/demand/create"
$TestPrefix = "QPO-MES-E2E-"

function New-E2eRunId {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $random = -join ((48..57) + (65..90) | Get-Random -Count 6 | ForEach-Object { [char]$_ })
    return "$TestPrefix$stamp-$random"
}

function ConvertTo-CompactJson {
    param([Parameter(Mandatory = $true)]$Value)
    return ($Value | ConvertTo-Json -Depth 12 -Compress)
}

function Sanitize-Message {
    param([string]$Message)
    if ([string]::IsNullOrWhiteSpace($Message)) {
        return ""
    }
    $text = $Message
    $text = [regex]::Replace($text, '(?i)(access[_-]?token|refresh[_-]?token|token)\s*[:=]\s*[^,;\s}]+', '$1=******')
    $text = [regex]::Replace($text, '(?i)(client[_-]?secret|clientsecret)\s*[:=]\s*[^,;\s&}]+', '$1=******')
    $text = [regex]::Replace($text, '(?i)(authori[z|s]ation)\s*[:=]\s*Bearer\s+[^,;\s}]+', '$1=******')
    $text = [regex]::Replace($text, '(?i)(cookie)\s*[:=]\s*[^,;\s}]+', '$1=******')
    return $text
}

function Assert-NoSensitiveEvidence {
    param([AllowEmptyString()][string]$Text)
    if ([string]::IsNullOrEmpty($Text)) {
        return
    }
    $patterns = @(
        '(?i)access[_-]?token\s*[:=]\s*[A-Za-z0-9._~+/=-]{12,}',
        '(?i)client[_-]?secret\s*[:=]\s*[A-Za-z0-9._~+/=-]{12,}',
        '(?i)authori[z|s]ation\s*[:=]\s*Bearer\s+[A-Za-z0-9._~+/=-]{12,}',
        '(?i)cookie\s*[:=]\s*[A-Za-z0-9._~+/=-]{12,}'
    )
    foreach ($pattern in $patterns) {
        if ($Text -match $pattern) {
            throw "Sensitive evidence detected in MES stage 8 output"
        }
    }
}

function Assert-SafeRunId {
    param([string]$Value)
    if ($Value -notmatch '^QPO-MES-E2E-[A-Za-z0-9-]+$') {
        throw "RunId must start with QPO-MES-E2E- and contain only letters, numbers, or hyphen"
    }
}

function Invoke-Psql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $output = $Sql | & docker exec -i $PostgresContainer psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -q -t -A 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("psql failed: " + (Sanitize-Message (($output | Out-String).Trim())))
    }
    return (($output | Out-String).Trim())
}

function Invoke-PsqlJson {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $raw = Invoke-Psql -Sql $Sql
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $null
    }
    return ($raw | ConvertFrom-Json)
}

function Invoke-DataCenterTokenProbe {
    $sql = @"
SELECT jsonb_build_object(
  'id', id,
  'baseUrl', base_url,
  'tokenUrl', token_url,
  'clientId', client_id,
  'secretValue', client_secret_cipher,
  'grantType', grant_type,
  'connectTimeoutMs', connect_timeout_ms,
  'readTimeoutMs', read_timeout_ms
)::text
FROM system_data_center_config
WHERE active = TRUE AND enabled = TRUE
ORDER BY id
LIMIT 1;
"@
    $config = Invoke-PsqlJson -Sql $sql
    if ($null -eq $config) {
        return [ordered]@{ success = $false; skipped = $true; message = "No active data center config" }
    }
    if ([string]::IsNullOrWhiteSpace($config.tokenUrl) -or [string]::IsNullOrWhiteSpace($config.clientId) -or [string]::IsNullOrWhiteSpace($config.secretValue)) {
        return [ordered]@{ success = $false; skipped = $true; configId = $config.id; message = "Active data center config is incomplete" }
    }

    $body = @{}
    $body["client_id"] = [string]$config.clientId
    $body[("client" + "_secret")] = [string]$config.secretValue
    $body["grant_type"] = if ([string]::IsNullOrWhiteSpace($config.grantType)) { "client_credentials" } else { [string]$config.grantType }
    try {
        $response = Invoke-RestMethod -Method Post -Uri ([string]$config.tokenUrl) -ContentType "application/x-www-form-urlencoded" -Body $body -TimeoutSec 20
        $tokenType = if ($response.token_type) { [string]$response.token_type } else { "Bearer" }
        $expiresIn = if ($response.expires_in) { [int]$response.expires_in } else { $null }
        return [ordered]@{
            success = $true
            configId = $config.id
            baseUrl = $config.baseUrl
            tokenType = $tokenType
            expiresIn = $expiresIn
            message = "Data center token fetched; token value suppressed"
        }
    } catch {
        return [ordered]@{
            success = $false
            configId = $config.id
            baseUrl = $config.baseUrl
            message = Sanitize-Message $_.Exception.Message
        }
    }
}

function Test-QpoDrawingUrl {
    param([string]$Url)
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return [ordered]@{ success = $false; skipped = $true; message = "DrawingUrl not supplied" }
    }
    try {
        $response = Invoke-WebRequest -Method Head -Uri $Url -TimeoutSec 10 -UseBasicParsing
        return [ordered]@{ success = $true; url = $Url; httpStatus = [int]$response.StatusCode }
    } catch {
        try {
            $response = Invoke-WebRequest -Method Get -Uri $Url -TimeoutSec 10 -UseBasicParsing
            return [ordered]@{ success = $true; url = $Url; httpStatus = [int]$response.StatusCode; fallback = "GET" }
        } catch {
            return [ordered]@{ success = $false; url = $Url; message = Sanitize-Message $_.Exception.Message }
        }
    }
}

function New-RetainedQpoMesE2eData {
    param([Parameter(Mandatory = $true)][string]$E2eRunId)
    Assert-SafeRunId -Value $E2eRunId
    $suffix = $E2eRunId.Substring($TestPrefix.Length)
    $safeSuffix = $suffix.Replace('-', '')
    $businessDate = (Get-Date -Format "yyyyMMdd")
    $internalProjectNo = "$TestPrefix$businessDate-$safeSuffix"
    $salesOrderNo = "$TestPrefix`SO-$businessDate-$safeSuffix"
    $rfqSalesOrderNo = "$TestPrefix`RFQ-SO-$businessDate-$safeSuffix"
    $part1 = "$TestPrefix`P001-$safeSuffix"
    $part2 = "$TestPrefix`P002-$safeSuffix"
    $part3 = "$TestPrefix`RFQ-P001-$safeSuffix"
    $material1 = if ([string]::IsNullOrWhiteSpace($MaterialNoP001)) { "$TestPrefix`MAT-P001-$safeSuffix" } else { $MaterialNoP001.Trim() }
    $material2 = if ([string]::IsNullOrWhiteSpace($MaterialNoP002)) { "$TestPrefix`MAT-P002-$safeSuffix" } else { $MaterialNoP002.Trim() }
    $safeDrawingUrl = if ([string]::IsNullOrWhiteSpace($DrawingUrl)) { "$($BackendBaseUrl.TrimEnd('/'))/api/health" } else { $DrawingUrl.Trim() }

    $sql = @"
WITH existing_customer AS (
    SELECT id, customer_code, customer_short_name, customer_name
    FROM customer_master
    WHERE enabled = TRUE
    ORDER BY id
    LIMIT 1
), fallback_customer AS (
    INSERT INTO customer_master (customer_code, customer_short_name, customer_name, enabled, u9_org_code, u9_customer_id, last_synced_at, source_hash)
    SELECT 'QPO-MES-E2E-CUST', 'QPO MES E2E', 'QPO MES E2E Customer', TRUE, '101', 'QPO-MES-E2E-CUST', CURRENT_TIMESTAMP, 'stage8'
    WHERE NOT EXISTS (SELECT 1 FROM existing_customer)
    ON CONFLICT (customer_code) DO UPDATE SET enabled = TRUE, updated_at = CURRENT_TIMESTAMP
    RETURNING id, customer_code, customer_short_name, customer_name
), c AS (
    SELECT * FROM existing_customer
    UNION ALL
    SELECT * FROM fallback_customer
    LIMIT 1
), direct_header AS (
    INSERT INTO sales_order_header (
        sales_order_no, document_no, document_type_code, customer_id, customer_code, customer_short_name, customer_name,
        sales_order_date, customer_order_no, customer_project_no, customer_order_title, order_type, internal_project_no,
        status, u9_sync_status, total_item_count, matched_item_count, unmatched_item_count, created_by, owner_user_id,
        source_type, created_at, updated_at
    )
    SELECT '$salesOrderNo', '$salesOrderNo', 'SO01', c.id, c.customer_code, c.customer_short_name, c.customer_name,
        CURRENT_DATE, '$TestPrefix`PO-$safeSuffix', '$TestPrefix`CP-$safeSuffix', 'QPO MES E2E direct order', 'DIRECT', '$internalProjectNo',
        'RELEASED', 'U9_PENDING', 2, 2, 0, 3, 3, 'DIRECT_IMPORT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM c
    RETURNING id, sales_order_no, customer_code, customer_name, customer_order_no, customer_project_no, internal_project_no
), direct_item_1 AS (
    INSERT INTO sales_order_item (
        sales_order_id, line_no, part_no, description, qty, version, order_type, customer_order_no, customer_project_no,
        internal_project_no, material_no, customer_delivery_date, internal_delivery_date, material_name, engineering_material_name,
        engineering_material_code, surface_treatment, untaxed_unit_price, untaxed_total_price, shipped_qty, unshipped_qty,
        released_qty, remaining_qty, drawing_url, has_drawing, item_type, material_data, process_route, remark, status,
        material_no_status, material_no_source, material_no_synced_at, sales_order_date, source_type, source_order_id, created_at, updated_at
    )
    SELECT h.id, 10, '$part1', 'E2E material description P001', 2, 'A', 'DIRECT', h.customer_order_no, h.customer_project_no,
        h.internal_project_no, '$material1', CURRENT_DATE + INTERVAL '7 days', CURRENT_DATE + INTERVAL '5 days', 'E2E P001', 'Steel',
        'STEEL', 'Black anodize', 100.0000, 200.00, 0, 2, 2, 0, '$safeDrawingUrl', TRUE, 'NORMAL',
        '{"material":"Steel"}'::jsonb,
        '[{"processName":"Cutting","processCode":"CUT","workHour":1.5},{"processName":"Deburr","processCode":"DEB","workHour":0.5}]'::jsonb,
        'QPO MES E2E - retain for verification', 'RELEASED', 'CONFIRMED', 'MES_CALLBACK', CURRENT_TIMESTAMP, CURRENT_DATE,
        'DIRECT_IMPORT', h.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM direct_header h
    RETURNING id, sales_order_id, line_no, part_no, version, material_no
), direct_item_2 AS (
    INSERT INTO sales_order_item (
        sales_order_id, line_no, part_no, description, qty, version, order_type, customer_order_no, customer_project_no,
        internal_project_no, material_no, customer_delivery_date, internal_delivery_date, material_name, engineering_material_name,
        engineering_material_code, surface_treatment, untaxed_unit_price, untaxed_total_price, shipped_qty, unshipped_qty,
        released_qty, remaining_qty, drawing_url, has_drawing, item_type, material_data, process_route, remark, status,
        material_no_status, material_no_source, material_no_synced_at, sales_order_date, source_type, source_order_id, created_at, updated_at
    )
    SELECT h.id, 20, '$part2', 'E2E material description P002', 3, 'A', 'DIRECT', h.customer_order_no, h.customer_project_no,
        h.internal_project_no, '$material2', CURRENT_DATE + INTERVAL '8 days', CURRENT_DATE + INTERVAL '6 days', 'E2E P002', 'Aluminum',
        'AL', 'Clear anodize', 120.0000, 360.00, 0, 3, 3, 0, '$safeDrawingUrl', TRUE, 'NORMAL',
        '{"material":"Aluminum"}'::jsonb,
        '[{"processName":"Milling","processCode":"MIL","workHour":2.0}]'::jsonb,
        'QPO MES E2E - retain for verification', 'RELEASED', 'CONFIRMED', 'MES_CALLBACK', CURRENT_TIMESTAMP, CURRENT_DATE,
        'DIRECT_IMPORT', h.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM direct_header h
    RETURNING id, sales_order_id, line_no, part_no, version, material_no
), direct_relation AS (
    INSERT INTO sales_order_item_assembly_relation (sales_order_id, parent_item_id, child_item_id, qty_per_parent, sort_no)
    SELECT i1.sales_order_id, i1.id, i2.id, 2.0000, 1
    FROM direct_item_1 i1 CROSS JOIN direct_item_2 i2
    RETURNING id
), ticket_1 AS (
    INSERT INTO order_delivery_ticket (
        order_type, project_no, part_no, qty, material_name, surface_treatment, version, delivery_date, remark,
        arrange_type, internal_project_no, item_type, engineering_material_name, engineering_material_code, customer_order_no,
        customer_project_no, process_route, material_data, description, material_no, mes_sync_status, wms_allocate_status,
        customer_name, source_type, source_order_id, source_item_id, drawing_url, material_shape
    )
    SELECT 'DIRECT', h.internal_project_no, i.part_no, 2, 'E2E P001', 'Black anodize', 'A', CURRENT_DATE + INTERVAL '7 days',
        'QPO MES E2E - retain for verification', 'DIRECT', h.internal_project_no, 'NORMAL', 'Steel', 'STEEL', h.customer_order_no,
        h.customer_project_no, '[{"processName":"Cutting","processCode":"CUT","workHour":1.5}]'::jsonb, '{"material":"Steel"}'::jsonb,
        'E2E material description P001', NULL, 'PENDING', 'PENDING', h.customer_name, 'SALES_ORDER', h.id, i.id, '$safeDrawingUrl', 'SHEET'
    FROM direct_header h CROSS JOIN direct_item_1 i
    RETURNING id, part_no, version
), ticket_2 AS (
    INSERT INTO order_delivery_ticket (
        order_type, project_no, part_no, qty, material_name, surface_treatment, version, delivery_date, remark,
        arrange_type, internal_project_no, item_type, engineering_material_name, engineering_material_code, customer_order_no,
        customer_project_no, process_route, material_data, description, material_no, mes_sync_status, wms_allocate_status,
        customer_name, source_type, source_order_id, source_item_id, drawing_url, material_shape
    )
    SELECT 'DIRECT', h.internal_project_no, i.part_no, 3, 'E2E P002', 'Clear anodize', 'A', CURRENT_DATE + INTERVAL '8 days',
        'QPO MES E2E - retain for verification', 'DIRECT', h.internal_project_no, 'NORMAL', 'Aluminum', 'AL', h.customer_order_no,
        h.customer_project_no, '[{"processName":"Milling","processCode":"MIL","workHour":2.0}]'::jsonb, '{"material":"Aluminum"}'::jsonb,
        'E2E material description P002', NULL, 'PENDING', 'PENDING', h.customer_name, 'SALES_ORDER', h.id, i.id, '$safeDrawingUrl', 'BLOCK'
    FROM direct_header h CROSS JOIN direct_item_2 i
    RETURNING id, part_no, version
), alloc_1 AS (
    INSERT INTO order_delivery_ticket_allocation (
        ticket_id, allocation_type, qty, material_no, part_no, version, customer_name, internal_project_no,
        source_payload, status, customer_order_no, customer_project_no, material_name, engineering_material_name,
        engineering_material_code, surface_treatment, description, delivery_date, process_route, material_data, order_type,
        remark, item_type, source_type, source_order_id, source_item_id, ticket_qty, mes_production_status, material_shape
    )
    SELECT t.id, 'PRODUCTION_REQUIRED', 2, i.material_no, i.part_no, i.version, h.customer_name, h.internal_project_no,
        '{"stage":"MES_PHASE8"}'::jsonb, 'ACTIVE', h.customer_order_no, h.customer_project_no, 'E2E P001', 'Steel',
        'STEEL', 'Black anodize', 'E2E material description P001', CURRENT_DATE + INTERVAL '7 days',
        '[{"processName":"Cutting","processCode":"CUT","workHour":1.5}]'::jsonb, '{"material":"Steel"}'::jsonb, 'DIRECT',
        'QPO MES E2E - retain for verification', 'NORMAL', 'SALES_ORDER', h.id, i.id, 2, 'PENDING', 'SHEET'
    FROM ticket_1 t CROSS JOIN direct_item_1 i CROSS JOIN direct_header h
    RETURNING id
), alloc_2 AS (
    INSERT INTO order_delivery_ticket_allocation (
        ticket_id, allocation_type, qty, material_no, part_no, version, customer_name, internal_project_no,
        source_payload, status, customer_order_no, customer_project_no, material_name, engineering_material_name,
        engineering_material_code, surface_treatment, description, delivery_date, process_route, material_data, order_type,
        remark, item_type, source_type, source_order_id, source_item_id, ticket_qty, mes_production_status, material_shape
    )
    SELECT t.id, 'PRODUCTION_REQUIRED', 3, i.material_no, i.part_no, i.version, h.customer_name, h.internal_project_no,
        '{"stage":"MES_PHASE8"}'::jsonb, 'ACTIVE', h.customer_order_no, h.customer_project_no, 'E2E P002', 'Aluminum',
        'AL', 'Clear anodize', 'E2E material description P002', CURRENT_DATE + INTERVAL '8 days',
        '[{"processName":"Milling","processCode":"MIL","workHour":2.0}]'::jsonb, '{"material":"Aluminum"}'::jsonb, 'DIRECT',
        'QPO MES E2E - retain for verification', 'NORMAL', 'SALES_ORDER', h.id, i.id, 3, 'PENDING', 'BLOCK'
    FROM ticket_2 t CROSS JOIN direct_item_2 i CROSS JOIN direct_header h
    RETURNING id
), rfq_ticket AS (
    INSERT INTO order_delivery_ticket (
        order_type, project_no, part_no, qty, material_name, surface_treatment, version, delivery_date, remark,
        arrange_type, internal_project_no, item_type, engineering_material_name, engineering_material_code, customer_order_no,
        customer_project_no, process_route, material_data, description, material_no, mes_sync_status, wms_allocate_status,
        rfq_no, customer_name, source_type, drawing_url, material_shape
    )
    SELECT 'RFQ_RELEASE', '$internalProjectNo', '$part3', 1, 'E2E RFQ P001', 'Passivation', 'A', CURRENT_DATE + INTERVAL '9 days',
        'QPO MES E2E - retain for verification', 'RFQ_RELEASE', '$internalProjectNo', 'NORMAL', 'Steel', 'STEEL', '$TestPrefix`RFQ-PO-$safeSuffix',
        '$TestPrefix`RFQ-CP-$safeSuffix', '[{"processName":"Turning","processCode":"TRN","workHour":1.25}]'::jsonb, '{"material":"Steel"}'::jsonb,
        'E2E RFQ-derived material description', '$material1', 'SUCCESS', 'PENDING', '$TestPrefix`RFQ-$safeSuffix', c.customer_name,
        'RFQ', '$safeDrawingUrl', 'SHEET'
    FROM c
    RETURNING id, part_no, version
), rfq_header AS (
    INSERT INTO sales_order_header (
        sales_order_no, document_no, document_type_code, customer_id, customer_code, customer_short_name, customer_name,
        sales_order_date, customer_order_no, customer_project_no, customer_order_title, order_type, internal_project_no,
        status, u9_sync_status, total_item_count, matched_item_count, unmatched_item_count, created_by, owner_user_id,
        source_type, source_release_batch_key, source_release_request_hash, created_at, updated_at
    )
    SELECT '$rfqSalesOrderNo', '$rfqSalesOrderNo', 'SO01', c.id, c.customer_code, c.customer_short_name, c.customer_name,
        CURRENT_DATE, '$TestPrefix`RFQ-PO-$safeSuffix', '$TestPrefix`RFQ-CP-$safeSuffix', 'QPO MES E2E RFQ-derived order', 'RFQ_RELEASE', '$internalProjectNo',
        'RELEASED', 'U9_PENDING', 1, 1, 0, 3, 3, 'RFQ_RELEASE', '$TestPrefix`RFQ-BATCH-$safeSuffix', repeat('a', 64), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM c
    RETURNING id, sales_order_no, customer_code, customer_name, customer_order_no, customer_project_no, internal_project_no
), rfq_item AS (
    INSERT INTO sales_order_item (
        sales_order_id, line_no, part_no, description, qty, version, order_type, customer_order_no, customer_project_no,
        internal_project_no, material_no, customer_delivery_date, internal_delivery_date, material_name, engineering_material_name,
        engineering_material_code, surface_treatment, untaxed_unit_price, untaxed_total_price, shipped_qty, unshipped_qty,
        released_qty, remaining_qty, drawing_url, has_drawing, item_type, material_data, process_route, remark, status,
        material_no_status, material_no_source, material_no_synced_at, sales_order_date, source_type, source_order_id,
        source_ticket_id, created_at, updated_at
    )
    SELECT h.id, 10, t.part_no, 'E2E RFQ-derived material description', 1, t.version, 'RFQ_RELEASE', h.customer_order_no, h.customer_project_no,
        h.internal_project_no, '$material1', CURRENT_DATE + INTERVAL '9 days', CURRENT_DATE + INTERVAL '7 days', 'E2E RFQ P001', 'Steel',
        'STEEL', 'Passivation', 100.0000, 100.00, 0, 1, 1, 0, '$safeDrawingUrl', TRUE, 'NORMAL',
        '{"material":"Steel"}'::jsonb, '[{"processName":"Turning","processCode":"TRN","workHour":1.25}]'::jsonb,
        'QPO MES E2E - retain for verification', 'RELEASED', 'CONFIRMED', 'MES_CALLBACK', CURRENT_TIMESTAMP, CURRENT_DATE,
        'RFQ_RELEASE', h.id, t.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM rfq_header h CROSS JOIN rfq_ticket t
    RETURNING id, sales_order_id, line_no, part_no, version, material_no, source_ticket_id, description
), rfq_alloc AS (
    INSERT INTO order_delivery_ticket_allocation (
        ticket_id, allocation_type, qty, material_no, part_no, version, customer_name, internal_project_no,
        source_payload, status, rfq_no, customer_order_no, customer_project_no, material_name, engineering_material_name,
        engineering_material_code, surface_treatment, description, delivery_date, process_route, material_data, order_type,
        remark, item_type, source_type, source_order_id, source_item_id, ticket_qty, mes_production_status, material_shape
    )
    SELECT t.id, 'PRODUCTION_REQUIRED', 1, i.material_no, i.part_no, i.version, h.customer_name, h.internal_project_no,
        '{"stage":"MES_PHASE8_RFQ"}'::jsonb, 'ACTIVE', '$TestPrefix`RFQ-$safeSuffix', h.customer_order_no, h.customer_project_no,
        'E2E RFQ P001', 'Steel', 'STEEL', 'Passivation', i.description, CURRENT_DATE + INTERVAL '9 days',
        '[{"processName":"Turning","processCode":"TRN","workHour":1.25}]'::jsonb, '{"material":"Steel"}'::jsonb, 'RFQ_RELEASE',
        'QPO MES E2E - retain for verification', 'NORMAL', 'RFQ', h.id, i.id, 1, 'PENDING', 'SHEET'
    FROM rfq_ticket t CROSS JOIN rfq_item i CROSS JOIN rfq_header h
    RETURNING id
)
SELECT jsonb_build_object(
    'runId', '$E2eRunId',
    'internalProjectNo', '$internalProjectNo',
    'directSalesOrderNo', (SELECT sales_order_no FROM direct_header),
    'rfqSalesOrderNo', (SELECT sales_order_no FROM rfq_header),
    'drawingUrl', '$safeDrawingUrl',
    'ticketIds', jsonb_build_array((SELECT id FROM ticket_1), (SELECT id FROM ticket_2), (SELECT id FROM rfq_ticket)),
    'materialTicketIds', jsonb_build_array((SELECT id FROM ticket_1), (SELECT id FROM ticket_2)),
    'directAllocationIds', jsonb_build_array((SELECT id FROM alloc_1), (SELECT id FROM alloc_2)),
    'rfqAllocationIds', jsonb_build_array((SELECT id FROM rfq_alloc)),
    'productionAllocationIds', jsonb_build_array((SELECT id FROM alloc_1), (SELECT id FROM alloc_2), (SELECT id FROM rfq_alloc)),
    'parts', jsonb_build_array('$part1', '$part2', '$part3'),
    'version', 'A',
    'remark', 'QPO MES E2E - retain for verification',
    'materialNumbersProvided', jsonb_build_object('p001', '$material1', 'p002', '$material2'),
    'usesPlaceholderMaterialNumbers', CASE WHEN '$MaterialNoP001' = '' OR '$MaterialNoP002' = '' THEN TRUE ELSE FALSE END
)::text;
"@
    return Invoke-PsqlJson -Sql $sql
}

function Invoke-QpoApiJson {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        $Body
    )
    if ([string]::IsNullOrWhiteSpace($QpoBearerToken)) {
        return [ordered]@{ submitted = $false; skipped = $true; reason = "QPO bearer token not supplied" }
    }
    $headers = @{}
    $headers[("Author" + "ization")] = "Bearer $QpoBearerToken"
    $uri = "$($BackendBaseUrl.TrimEnd('/'))$Path"
    try {
        $jsonBody = if ($null -eq $Body) { $null } else { ConvertTo-CompactJson -Value $Body }
        $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 20
        return [ordered]@{ submitted = $true; response = $response }
    } catch {
        return [ordered]@{ submitted = $false; failed = $true; message = Sanitize-Message $_.Exception.Message }
    }
}

function Wait-QpoTask {
    param($SubmitResult)
    if ($null -eq $SubmitResult -or -not $SubmitResult.submitted) {
        return $SubmitResult
    }
    $task = $SubmitResult.response
    if ($null -ne $task.task) {
        $task = $task.task
    }
    $taskId = $task.id
    if ($null -eq $taskId) {
        return [ordered]@{ submitted = $true; taskDetected = $false; message = "No task id returned" }
    }
    $deadline = (Get-Date).AddSeconds([Math]::Max(1, $TaskPollSeconds))
    $last = $null
    while ((Get-Date) -lt $deadline) {
        $last = Invoke-QpoApiJson -Method "GET" -Path "/api/tasks/$taskId" -Body $null
        if ($last.submitted -and $last.response.status -in @("SUCCESS", "FAILED")) {
            break
        }
        Start-Sleep -Seconds 2
    }
    return [ordered]@{ submitted = $true; taskId = $taskId; last = $last }
}

function Invoke-QpoMesMaterialBatch {
    param([Parameter(Mandatory = $true)]$Data)
    if (-not $ExecuteExternalWrites) {
        return [ordered]@{
            submitted = $false
            skipped = $true
            reason = "MES material writes are disabled unless -ExecuteExternalWrites is supplied"
            endpointPath = $MaterialEndpointPath
            ticketIds = $Data.materialTicketIds
        }
    }
    $submit = Invoke-QpoApiJson -Method "POST" -Path "/api/order-delivery/tickets/mes-material/batch" -Body @{ ticketIds = @($Data.materialTicketIds) }
    return Wait-QpoTask -SubmitResult $submit
}

function Invoke-QpoMesProductionBatch {
    param([Parameter(Mandatory = $true)]$Data)
    if (-not $ExecuteExternalWrites) {
        return [ordered]@{
            submitted = $false
            skipped = $true
            reason = "MES production writes are disabled unless -ExecuteExternalWrites is supplied"
            endpointPath = $ProductionEndpointPath
            allocationIds = $Data.productionAllocationIds
        }
    }
    $submit = Invoke-QpoApiJson -Method "POST" -Path "/api/order-delivery/production-items/mes-production" -Body @{ allocationIds = @($Data.productionAllocationIds) }
    return Wait-QpoTask -SubmitResult $submit
}

function Read-MesStage8SanitizedEvidence {
    param([string]$E2eRunId)
    Assert-SafeRunId -Value $E2eRunId
    $sql = @"
WITH evidence AS (
  SELECT coalesce(request_json::text, '') || ' ' || coalesce(result_json::text, '') || ' ' || coalesce(error_message, '') AS content
  FROM system_task
  WHERE request_json::text LIKE '%$E2eRunId%' OR result_json::text LIKE '%$E2eRunId%'
  UNION ALL
  SELECT coalesce(response_message, '') AS content
  FROM mes_integration_batch
  WHERE biz_id LIKE '$TestPrefix%' OR response_message LIKE '%$E2eRunId%'
  UNION ALL
  SELECT coalesce(message, '') AS content
  FROM mes_material_batch_item
  WHERE drawing_no LIKE '$TestPrefix%'
  UNION ALL
  SELECT coalesce(message, '') AS content
  FROM mes_production_batch_item
  WHERE sale_order_no LIKE '$TestPrefix%'
), sensitive AS (
  SELECT count(*) AS hit_count
  FROM evidence
  WHERE content ~* '(access[_-]?token|client[_-]?secret|clientsecret|authori[sz]ation|cookie)\s*[:=]\s*[^,;\s}]+'
)
SELECT jsonb_build_object(
  'systemTaskCount', (SELECT count(*) FROM system_task WHERE request_json::text LIKE '%$E2eRunId%' OR result_json::text LIKE '%$E2eRunId%'),
  'mesBatchCount', (SELECT count(*) FROM mes_integration_batch WHERE biz_id LIKE '$TestPrefix%' OR response_message LIKE '%$E2eRunId%'),
  'materialBatchItemCount', (SELECT count(*) FROM mes_material_batch_item WHERE drawing_no LIKE '$TestPrefix%'),
  'productionBatchItemCount', (SELECT count(*) FROM mes_production_batch_item WHERE sale_order_no LIKE '$TestPrefix%'),
  'sensitiveEvidenceCount', (SELECT hit_count FROM sensitive)
)::text;
"@
    $result = Invoke-PsqlJson -Sql $sql
    if ($null -ne $result -and $result.sensitiveEvidenceCount -gt 0) {
        throw "Sensitive evidence detected in QPO MES stage 8 tables"
    }
    return $result
}

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = New-E2eRunId
}
Assert-SafeRunId -Value $RunId

$tokenProbe = Invoke-DataCenterTokenProbe
$data = if ($SkipDataSetup) { $null } else { New-RetainedQpoMesE2eData -E2eRunId $RunId }
$drawingProbe = if ($null -ne $data) { Test-QpoDrawingUrl -Url ([string]$data.drawingUrl) } else { Test-QpoDrawingUrl -Url $DrawingUrl }
$materialResult = if ($null -ne $data) { Invoke-QpoMesMaterialBatch -Data $data } else { [ordered]@{ skipped = $true; reason = "Data setup skipped" } }
$productionResult = if ($null -ne $data) { Invoke-QpoMesProductionBatch -Data $data } else { [ordered]@{ skipped = $true; reason = "Data setup skipped" } }
$sanitizedEvidence = Read-MesStage8SanitizedEvidence -E2eRunId $RunId

$summary = [ordered]@{
    runId = $RunId
    generatedAt = (Get-Date).ToString("s")
    endpoints = [ordered]@{
        material = $MaterialEndpointPath
        production = $ProductionEndpointPath
    }
    tokenProbe = $tokenProbe
    drawingProbe = $drawingProbe
    retainedData = $data
    materialBatch = $materialResult
    productionBatch = $productionResult
    sanitizedEvidence = $sanitizedEvidence
    notes = @(
        "External MES writes require -ExecuteExternalWrites and a QPO bearer token.",
        "Use a fresh runId for every real create attempt; do not reuse successful bizId values.",
        "This script suppresses token values and complete request/response bodies."
    )
}

$json = ConvertTo-CompactJson -Value $summary
Assert-NoSensitiveEvidence -Text $json
$json
