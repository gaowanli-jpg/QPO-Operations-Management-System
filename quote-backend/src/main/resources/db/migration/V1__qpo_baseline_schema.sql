-- QPO compressed Flyway baseline schema.
-- Generated from the final Stage 12 schema; business data is intentionally excluded.

--
-- PostgreSQL database dump
--


-- Dumped from database version 15.18 (Debian 15.18-1.pgdg13+1)
-- Dumped by pg_dump version 15.18 (Debian 15.18-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path TO "${flyway:defaultSchema}";
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE audit_log (
    id integer NOT NULL,
    username character varying(80),
    role character varying(30),
    action character varying(100) NOT NULL,
    target_type character varying(80),
    target_id character varying(120),
    before_data text,
    after_data text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    module character varying(60),
    result character varying(30) DEFAULT 'SUCCESS'::character varying,
    message character varying(500),
    ip_address character varying(80),
    user_agent text,
    request_method character varying(20),
    request_path character varying(300),
    request_id character varying(80),
    error_message text
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE audit_log_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE audit_log_id_seq OWNED BY audit_log.id;


--
-- Name: audit_log_retention_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE audit_log_retention_config (
    id integer NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    retention_days integer DEFAULT 180 NOT NULL,
    archive_before_cleanup boolean DEFAULT true NOT NULL,
    cleanup_time character varying(5) DEFAULT '02:00'::character varying NOT NULL,
    last_run_at timestamp without time zone,
    last_run_deleted_count integer DEFAULT 0 NOT NULL,
    last_run_status character varying(30) DEFAULT 'NEVER'::character varying NOT NULL,
    last_run_message character varying(500),
    last_archive_file character varying(500),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT audit_log_retention_config_retention_days_check CHECK (((retention_days >= 1) AND (retention_days <= 3650)))
);


--
-- Name: customer_master; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE customer_master (
    id bigint NOT NULL,
    customer_code character varying(200) NOT NULL,
    customer_short_name character varying(200) NOT NULL,
    customer_name character varying(200) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: customer_master_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE customer_master_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_master_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE customer_master_id_seq OWNED BY customer_master.id;


--
-- Name: dict_assembly_markup_fee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_assembly_markup_fee (
    id integer NOT NULL,
    fee_name character varying(100) NOT NULL,
    rate_percent numeric(10,2) DEFAULT 0,
    sort_no integer,
    enabled boolean DEFAULT true
);


--
-- Name: dict_assembly_markup_fee_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_assembly_markup_fee_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_assembly_markup_fee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_assembly_markup_fee_id_seq OWNED BY dict_assembly_markup_fee.id;


--
-- Name: dict_extra_fee_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_extra_fee_item (
    id integer NOT NULL,
    fee_name character varying(100) NOT NULL,
    sort_no integer,
    enabled boolean DEFAULT true,
    remarks text
);


--
-- Name: dict_extra_fee_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_extra_fee_item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_extra_fee_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_extra_fee_item_id_seq OWNED BY dict_extra_fee_item.id;


--
-- Name: dict_markup_fee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_markup_fee (
    id integer NOT NULL,
    fee_name character varying(100) NOT NULL,
    rate_percent numeric(10,2) DEFAULT 0,
    sort_no integer,
    enabled boolean DEFAULT true
);


--
-- Name: dict_markup_fee_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_markup_fee_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_markup_fee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_markup_fee_id_seq OWNED BY dict_markup_fee.id;


--
-- Name: dict_material_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_material_type (
    id bigint NOT NULL,
    type_code character varying(64) NOT NULL,
    type_name character varying(120) NOT NULL,
    remarks character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    sort_no integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: dict_material_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_material_type_id_seq
    AS bigint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_material_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_material_type_id_seq OWNED BY dict_material_type.id;


--
-- Name: dict_material; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_material (
    id integer NOT NULL,
    material_code character varying(80) NOT NULL,
    material_name character varying(100) NOT NULL,
    material_type_id bigint NOT NULL,
    material_category character varying(80),
    density numeric(12,4),
    plate_density numeric(12,4),
    plate_price numeric(12,2),
    bar_density numeric(12,4),
    bar_price numeric(12,2),
    tube_density numeric(12,4),
    tube_price numeric(12,2),
    remarks text,
    enabled boolean DEFAULT true NOT NULL,
    sort_no integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: dict_material_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_material_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_material_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_material_id_seq OWNED BY dict_material.id;


--
-- Name: dict_process_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_process_category (
    id bigint NOT NULL,
    process_code character varying(64) NOT NULL,
    category_name character varying(120) NOT NULL,
    hourly_rate numeric(18,4) DEFAULT 0 NOT NULL,
    remarks character varying(500),
    source_type character varying(30) DEFAULT 'LOCAL'::character varying NOT NULL,
    mes_external_id character varying(120),
    enabled boolean DEFAULT true NOT NULL,
    sort_no integer DEFAULT 0 NOT NULL,
    mes_synced_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_dict_process_category_rate CHECK ((hourly_rate >= (0)::numeric)),
    CONSTRAINT chk_dict_process_category_source CHECK (((source_type)::text = ANY ((ARRAY['LOCAL'::character varying, 'MES'::character varying])::text[])))
);


--
-- Name: dict_process_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_process_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_process_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_process_category_id_seq OWNED BY dict_process_category.id;


--
-- Name: dict_process_detail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dict_process_detail (
    id bigint NOT NULL,
    category_id bigint NOT NULL,
    detail_name character varying(120) NOT NULL,
    detail_remarks character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    sort_no integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: dict_process_detail_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE dict_process_detail_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_process_detail_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE dict_process_detail_id_seq OWNED BY dict_process_detail.id;


--
-- Name: erp_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE erp_user (
    id integer NOT NULL,
    username character varying(80) NOT NULL,
    password_hash character varying(128) NOT NULL,
    display_name character varying(100),
    role character varying(30) NOT NULL,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    force_password_change boolean DEFAULT false NOT NULL,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    locked_until timestamp without time zone,
    last_login_at timestamp without time zone,
    password_changed_at timestamp without time zone
);


--
-- Name: erp_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE erp_user_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: erp_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE erp_user_id_seq OWNED BY erp_user.id;


--
-- Name: item_assembly_relation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE item_assembly_relation (
    id integer NOT NULL,
    rfq_id integer NOT NULL,
    parent_item_id integer NOT NULL,
    child_item_id integer NOT NULL,
    child_qty_per_parent numeric(14,4) DEFAULT 1 NOT NULL,
    sort_no integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_assembly_distinct_items CHECK ((parent_item_id <> child_item_id))
);


--
-- Name: item_assembly_relation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE item_assembly_relation_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_assembly_relation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE item_assembly_relation_id_seq OWNED BY item_assembly_relation.id;


--
-- Name: item_quote_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE item_quote_version (
    id integer NOT NULL,
    rfq_id integer,
    part_no character varying(100) NOT NULL,
    description text,
    qty integer NOT NULL,
    version character varying(20),
    order_type character varying(20),
    material_name character varying(255),
    surface_treatment character varying(255),
    unit character varying(50),
    lead_time character varying(100),
    remark text,
    material_data jsonb,
    process_route jsonb,
    fee_data jsonb,
    base_cost numeric(12,2),
    status character varying(50),
    drawing_url character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    quote_lead_time character varying(50),
    internal_target_lead_time character varying(50),
    material_gross_weight numeric(14,4),
    part_net_weight numeric(14,4) DEFAULT 0,
    quote_mode character varying(20) DEFAULT 'NORMAL'::character varying,
    assembly_fee numeric(12,2) DEFAULT 0,
    engineering_material_name character varying(255),
    engineering_material_code character varying(80),
    engineering_material_type_code character varying(64),
    engineering_material_type_name character varying(120),
    engineering_material_category character varying(80),
    sales_order_line_no integer,
    customer_order_no character varying(120),
    customer_project_no character varying(120),
    internal_project_no character varying(120),
    sales_material_no character varying(120),
    customer_delivery_date date,
    internal_delivery_date date,
    tax_included_unit_price numeric(12,2),
    untaxed_unit_price numeric(12,2),
    tax_included_total_price numeric(12,2),
    untaxed_total_price numeric(12,2),
    shipped_qty integer DEFAULT 0,
    unshipped_qty integer DEFAULT 0,
    sales_order_import_record_id bigint,
    material_shape character varying(30),
    customer_requirement text,
    material_no character varying(120),
    material_no_status character varying(30) DEFAULT '未确认'::character varying NOT NULL,
    material_no_source character varying(30),
    material_no_source_ticket_id bigint,
    material_no_synced_at timestamp without time zone,
    material_no_message text,
    sales_order_date date,
    customer_order_title character varying(255)
);


--
-- Name: item_quote_version_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE item_quote_version_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_quote_version_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE item_quote_version_id_seq OWNED BY item_quote_version.id;


--
-- Name: order_delivery_ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE order_delivery_ticket (
    id integer NOT NULL,
    rfq_id integer,
    item_id integer,
    order_type character varying(20),
    project_no character varying(100),
    part_no character varying(100),
    qty integer,
    material_name character varying(100),
    surface_treatment character varying(100),
    version character varying(20),
    start_date date,
    delivery_date date,
    remark text,
    arrange_type character varying(50),
    internal_project_no character varying(100),
    item_type character varying(20) DEFAULT '普通件'::character varying,
    engineering_material_name character varying(255),
    engineering_material_code character varying(80),
    engineering_material_type_code character varying(64),
    engineering_material_type_name character varying(120),
    engineering_material_category character varying(80),
    customer_order_no character varying(100),
    customer_project_no character varying(100),
    process_route jsonb DEFAULT '[]'::jsonb,
    material_data jsonb DEFAULT '{}'::jsonb,
    material_gross_weight numeric(14,4),
    part_net_weight numeric(14,4),
    description text,
    material_no character varying(100),
    mes_sync_status character varying(30) DEFAULT 'PENDING'::character varying,
    mes_sync_message text,
    mes_synced_at timestamp without time zone,
    wms_allocate_status character varying(30) DEFAULT 'PENDING'::character varying,
    wms_allocate_message text,
    wms_allocated_at timestamp without time zone,
    rfq_no character varying(100),
    customer_name character varying(255),
    source_type character varying(30) DEFAULT 'RFQ'::character varying NOT NULL,
    source_order_id bigint,
    source_item_id bigint,
    drawing_url character varying(500),
    mes_request_id character varying(80),
    mes_external_task_id character varying(120),
    mes_callback_received_at timestamp without time zone,
    mes_callback_message text,
    mes_dedup_key character varying(220),
    mes_request_role character varying(20),
    mes_master_ticket_id integer,
    mes_master_request_id character varying(80),
    material_shape character varying(30),
    customer_requirement text
);


--
-- Name: order_delivery_ticket_allocation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE order_delivery_ticket_allocation (
    id integer NOT NULL,
    ticket_id integer NOT NULL,
    rfq_id integer,
    item_id integer,
    allocation_type character varying(40) NOT NULL,
    qty integer NOT NULL,
    material_no character varying(100),
    part_no character varying(100),
    version character varying(20),
    customer_name character varying(255),
    internal_project_no character varying(100),
    wms_production_order_no character varying(100),
    source_payload jsonb DEFAULT '{}'::jsonb,
    status character varying(30) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    rfq_no character varying(100),
    customer_order_no character varying(100),
    customer_project_no character varying(100),
    material_name character varying(100),
    engineering_material_name character varying(255),
    engineering_material_code character varying(80),
    engineering_material_type_code character varying(64),
    engineering_material_type_name character varying(120),
    engineering_material_category character varying(80),
    surface_treatment character varying(100),
    description text,
    delivery_date date,
    process_route jsonb DEFAULT '[]'::jsonb,
    material_data jsonb DEFAULT '{}'::jsonb,
    material_gross_weight numeric(14,4),
    part_net_weight numeric(14,4),
    order_type character varying(20),
    remark text,
    item_type character varying(20) DEFAULT '普通件'::character varying,
    source_type character varying(30),
    source_order_id bigint,
    source_item_id bigint,
    ticket_qty integer,
    mes_production_status character varying(30) DEFAULT 'PENDING'::character varying,
    mes_production_request_id character varying(80),
    mes_production_external_id character varying(120),
    mes_production_synced_at timestamp without time zone,
    mes_production_message text,
    material_shape character varying(30),
    customer_requirement text,
    CONSTRAINT chk_order_delivery_ticket_allocation_qty CHECK ((qty > 0)),
    CONSTRAINT chk_order_delivery_ticket_allocation_type CHECK (((allocation_type)::text = ANY ((ARRAY['IN_STOCK_LOCK'::character varying, 'IN_PRODUCTION_LOCK'::character varying, 'PRODUCTION_REQUIRED'::character varying])::text[])))
);


--
-- Name: order_delivery_ticket_allocation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE order_delivery_ticket_allocation_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_delivery_ticket_allocation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE order_delivery_ticket_allocation_id_seq OWNED BY order_delivery_ticket_allocation.id;


--
-- Name: order_delivery_ticket_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE order_delivery_ticket_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_delivery_ticket_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE order_delivery_ticket_id_seq OWNED BY order_delivery_ticket.id;


--
-- Name: order_delivery_ticket_mes_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE order_delivery_ticket_mes_request (
    id bigint NOT NULL,
    ticket_id integer NOT NULL,
    mes_request_id character varying(80) NOT NULL,
    system_task_id bigint,
    request_status character varying(30) DEFAULT 'PROCESSING'::character varying NOT NULL,
    mes_external_task_id character varying(120),
    material_no character varying(100),
    request_message text,
    response_message text,
    callback_message text,
    requested_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    responded_at timestamp without time zone,
    callback_received_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dedup_key character varying(220),
    request_role character varying(20),
    follower_count integer DEFAULT 0,
    CONSTRAINT chk_delivery_mes_request_status CHECK (((request_status)::text = ANY ((ARRAY['PROCESSING'::character varying, 'CALLBACK_PENDING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'STALE'::character varying, 'IGNORED'::character varying])::text[])))
);


--
-- Name: order_delivery_ticket_mes_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE order_delivery_ticket_mes_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_delivery_ticket_mes_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE order_delivery_ticket_mes_request_id_seq OWNED BY order_delivery_ticket_mes_request.id;


--
-- Name: order_delivery_ticket_mes_waiter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE order_delivery_ticket_mes_waiter (
    id bigint NOT NULL,
    dedup_key character varying(220) NOT NULL,
    master_ticket_id integer NOT NULL,
    master_mes_request_id character varying(80) NOT NULL,
    waiter_ticket_id integer NOT NULL,
    wait_status character varying(30) DEFAULT 'WAITING'::character varying NOT NULL,
    material_no character varying(100),
    wait_message text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp without time zone,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_delivery_mes_waiter_distinct_ticket CHECK ((master_ticket_id <> waiter_ticket_id)),
    CONSTRAINT chk_delivery_mes_waiter_status CHECK (((wait_status)::text = ANY ((ARRAY['WAITING'::character varying, 'CALLBACK_PENDING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'STALE'::character varying, 'IGNORED'::character varying])::text[])))
);


--
-- Name: order_delivery_ticket_mes_waiter_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE order_delivery_ticket_mes_waiter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_delivery_ticket_mes_waiter_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE order_delivery_ticket_mes_waiter_id_seq OWNED BY order_delivery_ticket_mes_waiter.id;


--
-- Name: order_rfq; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE order_rfq (
    id integer NOT NULL,
    rfq_no character varying(100) NOT NULL,
    customer_name character varying(200),
    project_name character varying(255),
    status character varying(50),
    engineer_config jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by integer,
    owner_user_id integer,
    owner_dept_id bigint,
    order_type character varying(20),
    source_type character varying(30) DEFAULT 'RFQ'::character varying NOT NULL,
    customer_code character varying(200),
    customer_short_name character varying(200),
    sales_document_type_code character varying(50),
    sales_order_date date,
    customer_order_no character varying(120),
    customer_project_no character varying(120),
    customer_order_title character varying(200),
    internal_project_no character varying(120),
    sales_order_import_batch_id bigint,
    sales_order_import_record_id bigint
);


--
-- Name: order_rfq_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE order_rfq_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_rfq_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE order_rfq_id_seq OWNED BY order_rfq.id;


--
-- Name: rfq_no_sequence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE rfq_no_sequence (
    rfq_date date NOT NULL,
    last_seq integer DEFAULT 0 NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sales_order_header; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_order_header (
    id bigint NOT NULL,
    sales_order_no character varying(100) NOT NULL,
    document_no character varying(100),
    document_type_code character varying(50) DEFAULT 'SO01'::character varying NOT NULL,
    customer_id bigint,
    customer_code character varying(200),
    customer_short_name character varying(200),
    customer_name character varying(200),
    sales_order_date date,
    customer_order_no character varying(120),
    customer_project_no character varying(120),
    customer_order_title character varying(200),
    order_type character varying(50),
    internal_project_no character varying(120),
    status character varying(30) DEFAULT '已报价'::character varying NOT NULL,
    u9_sync_status character varying(30),
    u9_order_id character varying(100),
    u9_order_code character varying(100),
    import_batch_id bigint,
    import_record_id bigint,
    total_item_count integer DEFAULT 0,
    matched_item_count integer DEFAULT 0,
    unmatched_item_count integer DEFAULT 0,
    warning_message text,
    created_by bigint,
    owner_user_id integer,
    owner_dept_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    u9_sync_message text,
    source_type character varying(30) DEFAULT 'DIRECT_IMPORT'::character varying NOT NULL,
    source_release_batch_key character varying(100),
    source_release_request_hash character varying(64),
    CONSTRAINT chk_sales_order_header_release_source CHECK ((((source_type)::text <> 'RFQ_RELEASE'::text) OR ((NULLIF(btrim((source_release_batch_key)::text), ''::text) IS NOT NULL) AND (NULLIF(btrim((source_release_request_hash)::text), ''::text) IS NOT NULL))))
);


--
-- Name: sales_order_header_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sales_order_header_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_header_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sales_order_header_id_seq OWNED BY sales_order_header.id;


--
-- Name: sales_order_import_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_order_import_batch (
    id bigint NOT NULL,
    batch_no character varying(80) NOT NULL,
    source_file_name character varying(255),
    status character varying(30) DEFAULT 'PARSING'::character varying NOT NULL,
    total_order_count integer DEFAULT 0 NOT NULL,
    success_order_count integer DEFAULT 0 NOT NULL,
    failed_order_count integer DEFAULT 0 NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    import_mode character varying(20) DEFAULT 'U9'::character varying
);


--
-- Name: sales_order_import_batch_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sales_order_import_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_import_batch_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sales_order_import_batch_id_seq OWNED BY sales_order_import_batch.id;


--
-- Name: sales_order_import_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_order_import_record (
    id bigint NOT NULL,
    batch_id bigint NOT NULL,
    other_id character varying(120),
    customer_order_no character varying(120),
    order_by character varying(120),
    document_type character varying(120),
    line_count integer DEFAULT 0 NOT NULL,
    request_summary_json jsonb,
    u9_res_code integer,
    u9_success boolean,
    u9_id character varying(120),
    u9_code character varying(120),
    u9_error_msg text,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    rfq_id integer,
    rfq_no character varying(100),
    qpo_status character varying(30),
    matched_item_count integer DEFAULT 0,
    unmatched_item_count integer DEFAULT 0,
    warning_message text,
    sales_order_id bigint,
    sales_order_no character varying(100),
    item_count integer DEFAULT 0
);


--
-- Name: sales_order_import_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sales_order_import_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_import_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sales_order_import_record_id_seq OWNED BY sales_order_import_record.id;


--
-- Name: sales_order_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_order_item (
    id bigint NOT NULL,
    sales_order_id bigint NOT NULL,
    line_no integer,
    part_no character varying(100) NOT NULL,
    description text,
    qty integer NOT NULL,
    version character varying(50),
    order_type character varying(50),
    customer_order_no character varying(120),
    customer_project_no character varying(120),
    internal_project_no character varying(120),
    sales_material_no character varying(120),
    material_no character varying(120),
    customer_delivery_date date,
    internal_delivery_date date,
    material_name character varying(255),
    engineering_material_name character varying(255),
    engineering_material_code character varying(80),
    engineering_material_type_code character varying(64),
    engineering_material_type_name character varying(120),
    engineering_material_category character varying(80),
    surface_treatment character varying(255),
    tax_included_unit_price numeric(14,4),
    untaxed_unit_price numeric(14,4),
    tax_included_total_price numeric(14,2),
    untaxed_total_price numeric(14,2),
    shipped_qty integer DEFAULT 0,
    unshipped_qty integer DEFAULT 0,
    released_qty integer DEFAULT 0,
    remaining_qty integer DEFAULT 0,
    drawing_url character varying(500),
    has_drawing boolean DEFAULT false,
    material_gross_weight numeric(14,4),
    part_net_weight numeric(14,4),
    quote_mode character varying(20),
    assembly_fee numeric(12,2),
    base_cost numeric(12,2),
    item_type character varying(20),
    material_data jsonb DEFAULT '{}'::jsonb,
    process_route jsonb DEFAULT '[]'::jsonb,
    fee_data jsonb DEFAULT '[]'::jsonb,
    matched_rfq_id integer,
    matched_item_id integer,
    match_status character varying(30),
    match_message text,
    remark text,
    status character varying(30) DEFAULT '已报价'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    assembly_relation_status character varying(30) DEFAULT '不适用'::character varying,
    material_no_status character varying(30) DEFAULT '未确认'::character varying NOT NULL,
    material_no_source character varying(30),
    material_no_source_ticket_id bigint,
    material_no_synced_at timestamp without time zone,
    material_no_message text,
    material_shape character varying(30),
    customer_requirement text,
    sales_order_date date,
    customer_order_title character varying(255),
    source_type character varying(30) DEFAULT 'DIRECT_IMPORT'::character varying NOT NULL,
    source_order_id bigint,
    source_item_id bigint,
    source_ticket_id integer,
    CONSTRAINT chk_sales_order_item_qty CHECK ((qty > 0))
);


--
-- Name: sales_order_item_assembly_relation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_order_item_assembly_relation (
    id bigint NOT NULL,
    sales_order_id bigint NOT NULL,
    parent_item_id bigint NOT NULL,
    child_item_id bigint NOT NULL,
    qty_per_parent numeric(14,4) DEFAULT 1 NOT NULL,
    sort_no integer DEFAULT 0,
    source_parent_item_id integer,
    source_child_item_id integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_sales_order_assembly_distinct_items CHECK ((parent_item_id <> child_item_id))
);


--
-- Name: sales_order_item_assembly_relation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sales_order_item_assembly_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_item_assembly_relation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sales_order_item_assembly_relation_id_seq OWNED BY sales_order_item_assembly_relation.id;


--
-- Name: sales_order_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sales_order_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sales_order_item_id_seq OWNED BY sales_order_item.id;


--
-- Name: sales_order_source_rfq; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sales_order_source_rfq (
    id bigint NOT NULL,
    sales_order_id bigint NOT NULL,
    rfq_id integer NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sales_order_source_rfq_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sales_order_source_rfq_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_source_rfq_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sales_order_source_rfq_id_seq OWNED BY sales_order_source_rfq.id;


--
-- Name: system_data_center_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_data_center_config (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    active boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    base_url character varying(500),
    token_url character varying(500),
    client_id character varying(200),
    client_secret_cipher text,
    grant_type character varying(50) DEFAULT 'client_credentials'::character varying NOT NULL,
    connect_timeout_ms integer DEFAULT 5000 NOT NULL,
    read_timeout_ms integer DEFAULT 30000 NOT NULL,
    token_expire_skew_seconds integer DEFAULT 60 NOT NULL,
    last_test_status character varying(20),
    last_test_message character varying(500),
    last_test_at timestamp without time zone,
    remark character varying(500),
    created_by character varying(100),
    updated_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_data_center_grant_type CHECK (((grant_type)::text = 'client_credentials'::text)),
    CONSTRAINT ck_system_data_center_test_status CHECK (((last_test_status IS NULL) OR ((last_test_status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying])::text[]))))
);


--
-- Name: system_data_center_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_data_center_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_data_center_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_data_center_config_id_seq OWNED BY system_data_center_config.id;


--
-- Name: system_data_center_endpoint_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_data_center_endpoint_config (
    id bigint NOT NULL,
    endpoint_code character varying(80) NOT NULL,
    display_name character varying(100) NOT NULL,
    target_system_code character varying(30) NOT NULL,
    method character varying(10) DEFAULT 'POST'::character varying NOT NULL,
    path character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    timeout_ms integer,
    request_adapter character varying(80) NOT NULL,
    response_adapter character varying(80) NOT NULL,
    request_schema_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    response_schema_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    last_test_status character varying(20),
    last_test_message character varying(500),
    last_test_at timestamp without time zone,
    remark character varying(500),
    created_by character varying(100),
    updated_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_data_center_endpoint_method CHECK (((method)::text = ANY ((ARRAY['GET'::character varying, 'POST'::character varying])::text[]))),
    CONSTRAINT ck_system_data_center_endpoint_target CHECK (((target_system_code)::text = ANY ((ARRAY['MES'::character varying, 'WMS'::character varying, 'U9'::character varying])::text[]))),
    CONSTRAINT ck_system_data_center_endpoint_test_status CHECK (((last_test_status IS NULL) OR ((last_test_status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying])::text[]))))
);


--
-- Name: system_data_center_endpoint_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_data_center_endpoint_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_data_center_endpoint_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_data_center_endpoint_config_id_seq OWNED BY system_data_center_endpoint_config.id;


--
-- Name: system_dept; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_dept (
    id bigint NOT NULL,
    parent_id bigint DEFAULT 0,
    dept_code character varying(80) NOT NULL,
    dept_name character varying(100) NOT NULL,
    leader_user_id bigint,
    sort_order integer DEFAULT 0,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted boolean DEFAULT false
);


--
-- Name: system_dept_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_dept_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dept_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_dept_id_seq OWNED BY system_dept.id;


--
-- Name: system_external_integration_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_external_integration_config (
    id bigint NOT NULL,
    system_code character varying(30) NOT NULL,
    name character varying(100) NOT NULL,
    active boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    auth_type character varying(30) DEFAULT 'NONE'::character varying NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    remark character varying(500),
    last_test_status character varying(20),
    last_test_message character varying(500),
    last_test_at timestamp without time zone,
    created_by character varying(100),
    updated_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_external_integration_auth CHECK (((auth_type)::text = ANY ((ARRAY['NONE'::character varying, 'BEARER_TOKEN'::character varying, 'U9_AUTH_LOGIN'::character varying])::text[]))),
    CONSTRAINT ck_system_external_integration_system CHECK (((system_code)::text = ANY ((ARRAY['U9'::character varying, 'MES'::character varying, 'WMS'::character varying])::text[]))),
    CONSTRAINT ck_system_external_integration_test_status CHECK (((last_test_status IS NULL) OR ((last_test_status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying])::text[]))))
);


--
-- Name: system_external_integration_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_external_integration_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_external_integration_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_external_integration_config_id_seq OWNED BY system_external_integration_config.id;


--
-- Name: system_file_object; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_file_object (
    id bigint NOT NULL,
    public_path character varying(800) NOT NULL,
    storage_type character varying(20) NOT NULL,
    storage_config_id bigint,
    object_key character varying(800) NOT NULL,
    original_filename character varying(255),
    content_type character varying(100),
    size_bytes bigint,
    sha256 character varying(64),
    external_url character varying(1000),
    biz_type character varying(50),
    biz_id character varying(100),
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_file_object_size_nonnegative CHECK (((size_bytes IS NULL) OR (size_bytes >= 0))),
    CONSTRAINT ck_system_file_object_storage_type CHECK (((storage_type)::text = ANY ((ARRAY['LOCAL'::character varying, 'MINIO'::character varying])::text[])))
);


--
-- Name: system_file_object_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_file_object_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_file_object_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_file_object_id_seq OWNED BY system_file_object.id;


--
-- Name: system_file_storage_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_file_storage_config (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    storage_type character varying(20) NOT NULL,
    active boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    config_json jsonb NOT NULL,
    remark character varying(500),
    last_test_status character varying(20),
    last_test_message character varying(500),
    last_test_at timestamp without time zone,
    created_by character varying(100),
    updated_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_file_storage_config_test_status CHECK (((last_test_status IS NULL) OR ((last_test_status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying])::text[])))),
    CONSTRAINT ck_system_file_storage_config_type CHECK (((storage_type)::text = ANY ((ARRAY['LOCAL'::character varying, 'MINIO'::character varying])::text[])))
);


--
-- Name: system_file_storage_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_file_storage_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_file_storage_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_file_storage_config_id_seq OWNED BY system_file_storage_config.id;


--
-- Name: system_inbound_callback_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_inbound_callback_config (
    id bigint NOT NULL,
    callback_code character varying(60) NOT NULL,
    source_system_code character varying(30) NOT NULL,
    name character varying(100) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    token_enabled boolean DEFAULT true NOT NULL,
    public_base_url character varying(500),
    callback_path character varying(300) NOT NULL,
    token_hash character varying(128),
    token_mask character varying(30),
    token_updated_at timestamp without time zone,
    last_validate_status character varying(20),
    last_validate_message character varying(500),
    last_validate_at timestamp without time zone,
    remark character varying(500),
    created_by character varying(100),
    updated_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_inbound_callback_source CHECK (((source_system_code)::text = 'MES'::text)),
    CONSTRAINT ck_system_inbound_callback_validate_status CHECK (((last_validate_status IS NULL) OR ((last_validate_status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying])::text[]))))
);


--
-- Name: system_inbound_callback_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_inbound_callback_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_inbound_callback_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_inbound_callback_config_id_seq OWNED BY system_inbound_callback_config.id;


--
-- Name: system_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_permission (
    id bigint NOT NULL,
    parent_id bigint DEFAULT 0,
    permission_type character varying(20) NOT NULL,
    permission_code character varying(120) NOT NULL,
    permission_name character varying(100) NOT NULL,
    route_path character varying(200),
    component_key character varying(120),
    icon character varying(80),
    sort_order integer DEFAULT 0,
    visible boolean DEFAULT false,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    assignable boolean DEFAULT true
);


--
-- Name: system_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_permission_id_seq OWNED BY system_permission.id;


--
-- Name: system_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_post (
    id bigint NOT NULL,
    post_code character varying(80) NOT NULL,
    post_name character varying(100) NOT NULL,
    sort_order integer DEFAULT 0,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted boolean DEFAULT false
);


--
-- Name: system_post_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_post_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_post_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_post_id_seq OWNED BY system_post.id;


--
-- Name: system_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_role (
    id bigint NOT NULL,
    role_code character varying(80) NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(255),
    super_admin boolean DEFAULT false,
    data_scope character varying(40) DEFAULT 'ALL'::character varying,
    enabled boolean DEFAULT true,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted boolean DEFAULT false
);


--
-- Name: system_role_data_scope_customer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_role_data_scope_customer (
    role_id bigint NOT NULL,
    customer_name character varying(200) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: system_role_data_scope_dept; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_role_data_scope_dept (
    role_id bigint NOT NULL,
    dept_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: system_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_role_id_seq OWNED BY system_role.id;


--
-- Name: system_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: system_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_task (
    id bigint NOT NULL,
    task_type character varying(80) NOT NULL,
    status character varying(20) NOT NULL,
    progress integer DEFAULT 0 NOT NULL,
    total integer DEFAULT 1 NOT NULL,
    request_json jsonb,
    result_json jsonb,
    file_path text,
    error_message text,
    created_by character varying(80),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at timestamp without time zone,
    finished_at timestamp without time zone
);


--
-- Name: system_task_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_task_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_task_id_seq OWNED BY system_task.id;


--
-- Name: system_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_user (
    id bigint NOT NULL,
    legacy_user_id integer,
    username character varying(80) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(100),
    real_name character varying(100),
    phone character varying(50),
    email character varying(120),
    dept_id bigint,
    post_id bigint,
    legacy_role character varying(30),
    enabled boolean DEFAULT true,
    force_password_change boolean DEFAULT false,
    failed_login_attempts integer DEFAULT 0,
    locked_until timestamp without time zone,
    last_login_at timestamp without time zone,
    password_changed_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted boolean DEFAULT false
);


--
-- Name: system_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_user_id_seq OWNED BY system_user.id;


--
-- Name: system_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE system_user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_log ALTER COLUMN id SET DEFAULT nextval('audit_log_id_seq'::regclass);


--
-- Name: customer_master id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY customer_master ALTER COLUMN id SET DEFAULT nextval('customer_master_id_seq'::regclass);


--
-- Name: dict_assembly_markup_fee id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_assembly_markup_fee ALTER COLUMN id SET DEFAULT nextval('dict_assembly_markup_fee_id_seq'::regclass);


--
-- Name: dict_extra_fee_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_extra_fee_item ALTER COLUMN id SET DEFAULT nextval('dict_extra_fee_item_id_seq'::regclass);


--
-- Name: dict_markup_fee id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_markup_fee ALTER COLUMN id SET DEFAULT nextval('dict_markup_fee_id_seq'::regclass);


--
-- Name: dict_material_type id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_material_type ALTER COLUMN id SET DEFAULT nextval('dict_material_type_id_seq'::regclass);


--
-- Name: dict_material id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_material ALTER COLUMN id SET DEFAULT nextval('dict_material_id_seq'::regclass);


--
-- Name: dict_process_category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_category ALTER COLUMN id SET DEFAULT nextval('dict_process_category_id_seq'::regclass);


--
-- Name: dict_process_detail id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_detail ALTER COLUMN id SET DEFAULT nextval('dict_process_detail_id_seq'::regclass);


--
-- Name: erp_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY erp_user ALTER COLUMN id SET DEFAULT nextval('erp_user_id_seq'::regclass);


--
-- Name: item_assembly_relation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_assembly_relation ALTER COLUMN id SET DEFAULT nextval('item_assembly_relation_id_seq'::regclass);


--
-- Name: item_quote_version id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_quote_version ALTER COLUMN id SET DEFAULT nextval('item_quote_version_id_seq'::regclass);


--
-- Name: order_delivery_ticket id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket ALTER COLUMN id SET DEFAULT nextval('order_delivery_ticket_id_seq'::regclass);


--
-- Name: order_delivery_ticket_allocation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_allocation ALTER COLUMN id SET DEFAULT nextval('order_delivery_ticket_allocation_id_seq'::regclass);


--
-- Name: order_delivery_ticket_mes_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_request ALTER COLUMN id SET DEFAULT nextval('order_delivery_ticket_mes_request_id_seq'::regclass);


--
-- Name: order_delivery_ticket_mes_waiter id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_waiter ALTER COLUMN id SET DEFAULT nextval('order_delivery_ticket_mes_waiter_id_seq'::regclass);


--
-- Name: order_rfq id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_rfq ALTER COLUMN id SET DEFAULT nextval('order_rfq_id_seq'::regclass);


--
-- Name: sales_order_header id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_header ALTER COLUMN id SET DEFAULT nextval('sales_order_header_id_seq'::regclass);


--
-- Name: sales_order_import_batch id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_import_batch ALTER COLUMN id SET DEFAULT nextval('sales_order_import_batch_id_seq'::regclass);


--
-- Name: sales_order_import_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_import_record ALTER COLUMN id SET DEFAULT nextval('sales_order_import_record_id_seq'::regclass);


--
-- Name: sales_order_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item ALTER COLUMN id SET DEFAULT nextval('sales_order_item_id_seq'::regclass);


--
-- Name: sales_order_item_assembly_relation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item_assembly_relation ALTER COLUMN id SET DEFAULT nextval('sales_order_item_assembly_relation_id_seq'::regclass);


--
-- Name: sales_order_source_rfq id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_source_rfq ALTER COLUMN id SET DEFAULT nextval('sales_order_source_rfq_id_seq'::regclass);


--
-- Name: system_data_center_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_data_center_config ALTER COLUMN id SET DEFAULT nextval('system_data_center_config_id_seq'::regclass);


--
-- Name: system_data_center_endpoint_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_data_center_endpoint_config ALTER COLUMN id SET DEFAULT nextval('system_data_center_endpoint_config_id_seq'::regclass);


--
-- Name: system_dept id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_dept ALTER COLUMN id SET DEFAULT nextval('system_dept_id_seq'::regclass);


--
-- Name: system_external_integration_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_external_integration_config ALTER COLUMN id SET DEFAULT nextval('system_external_integration_config_id_seq'::regclass);


--
-- Name: system_file_object id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_file_object ALTER COLUMN id SET DEFAULT nextval('system_file_object_id_seq'::regclass);


--
-- Name: system_file_storage_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_file_storage_config ALTER COLUMN id SET DEFAULT nextval('system_file_storage_config_id_seq'::regclass);


--
-- Name: system_inbound_callback_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_inbound_callback_config ALTER COLUMN id SET DEFAULT nextval('system_inbound_callback_config_id_seq'::regclass);


--
-- Name: system_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_permission ALTER COLUMN id SET DEFAULT nextval('system_permission_id_seq'::regclass);


--
-- Name: system_post id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_post ALTER COLUMN id SET DEFAULT nextval('system_post_id_seq'::regclass);


--
-- Name: system_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role ALTER COLUMN id SET DEFAULT nextval('system_role_id_seq'::regclass);


--
-- Name: system_task id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_task ALTER COLUMN id SET DEFAULT nextval('system_task_id_seq'::regclass);


--
-- Name: system_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user ALTER COLUMN id SET DEFAULT nextval('system_user_id_seq'::regclass);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: audit_log_retention_config audit_log_retention_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_log_retention_config
    ADD CONSTRAINT audit_log_retention_config_pkey PRIMARY KEY (id);


--
-- Name: customer_master customer_master_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY customer_master
    ADD CONSTRAINT customer_master_pkey PRIMARY KEY (id);


--
-- Name: dict_assembly_markup_fee dict_assembly_markup_fee_fee_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_assembly_markup_fee
    ADD CONSTRAINT dict_assembly_markup_fee_fee_name_key UNIQUE (fee_name);


--
-- Name: dict_assembly_markup_fee dict_assembly_markup_fee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_assembly_markup_fee
    ADD CONSTRAINT dict_assembly_markup_fee_pkey PRIMARY KEY (id);


--
-- Name: dict_extra_fee_item dict_extra_fee_item_fee_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_extra_fee_item
    ADD CONSTRAINT dict_extra_fee_item_fee_name_key UNIQUE (fee_name);


--
-- Name: dict_extra_fee_item dict_extra_fee_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_extra_fee_item
    ADD CONSTRAINT dict_extra_fee_item_pkey PRIMARY KEY (id);


--
-- Name: dict_markup_fee dict_markup_fee_fee_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_markup_fee
    ADD CONSTRAINT dict_markup_fee_fee_name_key UNIQUE (fee_name);


--
-- Name: dict_markup_fee dict_markup_fee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_markup_fee
    ADD CONSTRAINT dict_markup_fee_pkey PRIMARY KEY (id);


--
-- Name: dict_material_type dict_material_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_material_type
    ADD CONSTRAINT dict_material_type_pkey PRIMARY KEY (id);


--
-- Name: dict_material dict_material_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_material
    ADD CONSTRAINT dict_material_pkey PRIMARY KEY (id);


--
-- Name: dict_process_category dict_process_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_category
    ADD CONSTRAINT dict_process_category_pkey PRIMARY KEY (id);


--
-- Name: dict_process_detail dict_process_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_detail
    ADD CONSTRAINT dict_process_detail_pkey PRIMARY KEY (id);


--
-- Name: erp_user erp_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY erp_user
    ADD CONSTRAINT erp_user_pkey PRIMARY KEY (id);


--
-- Name: erp_user erp_user_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY erp_user
    ADD CONSTRAINT erp_user_username_key UNIQUE (username);


--
-- Name: item_assembly_relation item_assembly_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_assembly_relation
    ADD CONSTRAINT item_assembly_relation_pkey PRIMARY KEY (id);


--
-- Name: item_quote_version item_quote_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_quote_version
    ADD CONSTRAINT item_quote_version_pkey PRIMARY KEY (id);


--
-- Name: order_delivery_ticket_allocation order_delivery_ticket_allocation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_allocation
    ADD CONSTRAINT order_delivery_ticket_allocation_pkey PRIMARY KEY (id);


--
-- Name: order_delivery_ticket_mes_request order_delivery_ticket_mes_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_request
    ADD CONSTRAINT order_delivery_ticket_mes_request_pkey PRIMARY KEY (id);


--
-- Name: order_delivery_ticket_mes_waiter order_delivery_ticket_mes_waiter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_waiter
    ADD CONSTRAINT order_delivery_ticket_mes_waiter_pkey PRIMARY KEY (id);


--
-- Name: order_delivery_ticket order_delivery_ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket
    ADD CONSTRAINT order_delivery_ticket_pkey PRIMARY KEY (id);


--
-- Name: order_rfq order_rfq_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_rfq
    ADD CONSTRAINT order_rfq_pkey PRIMARY KEY (id);


--
-- Name: order_rfq order_rfq_rfq_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_rfq
    ADD CONSTRAINT order_rfq_rfq_no_key UNIQUE (rfq_no);


--
-- Name: rfq_no_sequence rfq_no_sequence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY rfq_no_sequence
    ADD CONSTRAINT rfq_no_sequence_pkey PRIMARY KEY (rfq_date);


--
-- Name: sales_order_header sales_order_header_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_header
    ADD CONSTRAINT sales_order_header_pkey PRIMARY KEY (id);


--
-- Name: sales_order_import_batch sales_order_import_batch_batch_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_import_batch
    ADD CONSTRAINT sales_order_import_batch_batch_no_key UNIQUE (batch_no);


--
-- Name: sales_order_import_batch sales_order_import_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_import_batch
    ADD CONSTRAINT sales_order_import_batch_pkey PRIMARY KEY (id);


--
-- Name: sales_order_import_record sales_order_import_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_import_record
    ADD CONSTRAINT sales_order_import_record_pkey PRIMARY KEY (id);


--
-- Name: sales_order_item_assembly_relation sales_order_item_assembly_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item_assembly_relation
    ADD CONSTRAINT sales_order_item_assembly_relation_pkey PRIMARY KEY (id);


--
-- Name: sales_order_item sales_order_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item
    ADD CONSTRAINT sales_order_item_pkey PRIMARY KEY (id);


--
-- Name: sales_order_source_rfq sales_order_source_rfq_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_source_rfq
    ADD CONSTRAINT sales_order_source_rfq_pkey PRIMARY KEY (id);


--
-- Name: system_data_center_config system_data_center_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_data_center_config
    ADD CONSTRAINT system_data_center_config_pkey PRIMARY KEY (id);


--
-- Name: system_data_center_endpoint_config system_data_center_endpoint_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_data_center_endpoint_config
    ADD CONSTRAINT system_data_center_endpoint_config_pkey PRIMARY KEY (id);


--
-- Name: system_dept system_dept_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_dept
    ADD CONSTRAINT system_dept_pkey PRIMARY KEY (id);


--
-- Name: system_external_integration_config system_external_integration_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_external_integration_config
    ADD CONSTRAINT system_external_integration_config_pkey PRIMARY KEY (id);


--
-- Name: system_file_object system_file_object_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_file_object
    ADD CONSTRAINT system_file_object_pkey PRIMARY KEY (id);


--
-- Name: system_file_storage_config system_file_storage_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_file_storage_config
    ADD CONSTRAINT system_file_storage_config_pkey PRIMARY KEY (id);


--
-- Name: system_inbound_callback_config system_inbound_callback_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_inbound_callback_config
    ADD CONSTRAINT system_inbound_callback_config_pkey PRIMARY KEY (id);


--
-- Name: system_permission system_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_permission
    ADD CONSTRAINT system_permission_pkey PRIMARY KEY (id);


--
-- Name: system_post system_post_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_post
    ADD CONSTRAINT system_post_pkey PRIMARY KEY (id);


--
-- Name: system_role system_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role
    ADD CONSTRAINT system_role_pkey PRIMARY KEY (id);


--
-- Name: system_task system_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_task
    ADD CONSTRAINT system_task_pkey PRIMARY KEY (id);


--
-- Name: system_user system_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user
    ADD CONSTRAINT system_user_pkey PRIMARY KEY (id);


--
-- Name: customer_master uk_customer_master_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY customer_master
    ADD CONSTRAINT uk_customer_master_code UNIQUE (customer_code);


--
-- Name: customer_master uk_customer_master_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY customer_master
    ADD CONSTRAINT uk_customer_master_name UNIQUE (customer_name);


--
-- Name: customer_master uk_customer_master_short_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY customer_master
    ADD CONSTRAINT uk_customer_master_short_name UNIQUE (customer_short_name);


--
-- Name: dict_process_category uk_dict_process_category_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_category
    ADD CONSTRAINT uk_dict_process_category_code UNIQUE (process_code);


--
-- Name: dict_process_detail uk_dict_process_detail_category_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_detail
    ADD CONSTRAINT uk_dict_process_detail_category_name UNIQUE (category_id, detail_name);


--
-- Name: sales_order_source_rfq uk_sales_order_source_rfq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_source_rfq
    ADD CONSTRAINT uk_sales_order_source_rfq UNIQUE (sales_order_id, rfq_id);


--
-- Name: system_data_center_endpoint_config uk_system_data_center_endpoint_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_data_center_endpoint_config
    ADD CONSTRAINT uk_system_data_center_endpoint_code UNIQUE (endpoint_code);


--
-- Name: system_dept uk_system_dept_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_dept
    ADD CONSTRAINT uk_system_dept_code UNIQUE (dept_code);


--
-- Name: system_inbound_callback_config uk_system_inbound_callback_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_inbound_callback_config
    ADD CONSTRAINT uk_system_inbound_callback_code UNIQUE (callback_code);


--
-- Name: system_permission uk_system_permission_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_permission
    ADD CONSTRAINT uk_system_permission_code UNIQUE (permission_code);


--
-- Name: system_post uk_system_post_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_post
    ADD CONSTRAINT uk_system_post_code UNIQUE (post_code);


--
-- Name: system_role uk_system_role_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role
    ADD CONSTRAINT uk_system_role_code UNIQUE (role_code);


--
-- Name: system_role_data_scope_customer uk_system_role_data_scope_customer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_data_scope_customer
    ADD CONSTRAINT uk_system_role_data_scope_customer UNIQUE (role_id, customer_name);


--
-- Name: system_role_data_scope_dept uk_system_role_data_scope_dept; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_data_scope_dept
    ADD CONSTRAINT uk_system_role_data_scope_dept UNIQUE (role_id, dept_id);


--
-- Name: system_role_permission uk_system_role_permission; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_permission
    ADD CONSTRAINT uk_system_role_permission UNIQUE (role_id, permission_id);


--
-- Name: system_user uk_system_user_legacy_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user
    ADD CONSTRAINT uk_system_user_legacy_user_id UNIQUE (legacy_user_id);


--
-- Name: system_user_role uk_system_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user_role
    ADD CONSTRAINT uk_system_user_role UNIQUE (user_id, role_id);


--
-- Name: system_user uk_system_user_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user
    ADD CONSTRAINT uk_system_user_username UNIQUE (username);


--
-- Name: idx_assembly_child; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assembly_child ON item_assembly_relation USING btree (child_item_id);


--
-- Name: idx_assembly_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assembly_parent ON item_assembly_relation USING btree (parent_item_id);


--
-- Name: idx_assembly_parent_child; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_assembly_parent_child ON item_assembly_relation USING btree (parent_item_id, child_item_id);


--
-- Name: idx_assembly_rfq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assembly_rfq ON item_assembly_relation USING btree (rfq_id);


--
-- Name: idx_audit_log_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_action ON audit_log USING btree (action);


--
-- Name: idx_audit_log_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_created_at ON audit_log USING btree (created_at);


--
-- Name: idx_audit_log_module; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_module ON audit_log USING btree (module);


--
-- Name: idx_audit_log_request_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_request_id ON audit_log USING btree (request_id);


--
-- Name: idx_audit_log_result; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_result ON audit_log USING btree (result);


--
-- Name: idx_audit_log_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_target ON audit_log USING btree (target_type, target_id);


--
-- Name: idx_audit_log_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_username ON audit_log USING btree (username);


--
-- Name: idx_customer_master_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_master_enabled ON customer_master USING btree (enabled);


--
-- Name: idx_delivery_allocation_delivery_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_delivery_date ON order_delivery_ticket_allocation USING btree (delivery_date, id);


--
-- Name: idx_delivery_allocation_internal_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_internal_project ON order_delivery_ticket_allocation USING btree (internal_project_no, id);


--
-- Name: idx_delivery_allocation_item_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_item_type ON order_delivery_ticket_allocation USING btree (item_type, id);


--
-- Name: idx_delivery_allocation_locked_stock_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_locked_stock_id ON order_delivery_ticket_allocation USING btree (allocation_type, id DESC) WHERE ((allocation_type)::text = ANY ((ARRAY['IN_STOCK_LOCK'::character varying, 'IN_PRODUCTION_LOCK'::character varying])::text[]));


--
-- Name: idx_delivery_allocation_material; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_material ON order_delivery_ticket_allocation USING btree (material_no, id);


--
-- Name: idx_delivery_allocation_mes_production_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_mes_production_status ON order_delivery_ticket_allocation USING btree (mes_production_status, allocation_type, id DESC);


--
-- Name: idx_delivery_allocation_mes_production_status_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_mes_production_status_id ON order_delivery_ticket_allocation USING btree (mes_production_status, id DESC);


--
-- Name: idx_delivery_allocation_part_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_part_version ON order_delivery_ticket_allocation USING btree (part_no, version, id);


--
-- Name: idx_delivery_allocation_production_active_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_production_active_id ON order_delivery_ticket_allocation USING btree (id DESC) WHERE (((allocation_type)::text = 'PRODUCTION_REQUIRED'::text) AND ((mes_production_status IS NULL) OR (TRIM(BOTH FROM mes_production_status) = ''::text) OR ((mes_production_status)::text <> 'SUCCESS'::text)));


--
-- Name: idx_delivery_allocation_rfq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_rfq ON order_delivery_ticket_allocation USING btree (rfq_id, id);


--
-- Name: idx_delivery_allocation_rfq_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_rfq_no ON order_delivery_ticket_allocation USING btree (rfq_no, id);


--
-- Name: idx_delivery_allocation_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_source ON order_delivery_ticket_allocation USING btree (source_type, source_order_id, source_item_id);


--
-- Name: idx_delivery_allocation_ticket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_ticket ON order_delivery_ticket_allocation USING btree (ticket_id, id);


--
-- Name: idx_delivery_allocation_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_type ON order_delivery_ticket_allocation USING btree (allocation_type, id);


--
-- Name: idx_delivery_allocation_wms_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_allocation_wms_order ON order_delivery_ticket_allocation USING btree (wms_production_order_no, id);


--
-- Name: idx_delivery_mes_request_dedup_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_mes_request_dedup_status ON order_delivery_ticket_mes_request USING btree (dedup_key, request_status, id DESC) WHERE ((dedup_key IS NOT NULL) AND (TRIM(BOTH FROM dedup_key) <> ''::text));


--
-- Name: idx_delivery_mes_request_external_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_mes_request_external_task ON order_delivery_ticket_mes_request USING btree (mes_external_task_id, id) WHERE ((mes_external_task_id IS NOT NULL) AND (TRIM(BOTH FROM mes_external_task_id) <> ''::text));


--
-- Name: idx_delivery_mes_request_ticket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_mes_request_ticket ON order_delivery_ticket_mes_request USING btree (ticket_id, id DESC);


--
-- Name: idx_delivery_mes_waiter_dedup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_mes_waiter_dedup ON order_delivery_ticket_mes_waiter USING btree (dedup_key, wait_status, id);


--
-- Name: idx_delivery_mes_waiter_master_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_mes_waiter_master_request ON order_delivery_ticket_mes_waiter USING btree (master_mes_request_id, wait_status, id);


--
-- Name: idx_delivery_ticket_mes_dedup_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_ticket_mes_dedup_key ON order_delivery_ticket USING btree (mes_dedup_key, mes_sync_status, id) WHERE ((mes_dedup_key IS NOT NULL) AND (TRIM(BOTH FROM mes_dedup_key) <> ''::text));


--
-- Name: idx_delivery_ticket_mes_master_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_ticket_mes_master_request ON order_delivery_ticket USING btree (mes_master_request_id, id) WHERE ((mes_master_request_id IS NOT NULL) AND (TRIM(BOTH FROM mes_master_request_id) <> ''::text));


--
-- Name: idx_dict_material_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_material_category ON dict_material USING btree (material_category, id);


--
-- Name: idx_dict_material_material_type_enabled_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_material_material_type_enabled_sort ON dict_material USING btree (material_type_id, enabled, sort_no, id);


--
-- Name: idx_dict_material_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_material_name ON dict_material USING btree (material_name, id);


--
-- Name: idx_dict_material_type_enabled_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_material_type_enabled_sort ON dict_material_type USING btree (enabled, sort_no, id);


--
-- Name: uk_dict_material_code_upper; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dict_material_code_upper ON dict_material USING btree (upper((material_code)::text));


--
-- Name: uk_dict_material_type_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dict_material_type_code ON dict_material_type USING btree (type_code);


--
-- Name: uk_dict_material_type_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dict_material_type_name ON dict_material_type USING btree (type_name);


--
-- Name: idx_dict_process_category_enabled_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_process_category_enabled_sort ON dict_process_category USING btree (enabled, sort_no, id);


--
-- Name: idx_dict_process_category_mes_external; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_process_category_mes_external ON dict_process_category USING btree (mes_external_id) WHERE (mes_external_id IS NOT NULL);


--
-- Name: idx_dict_process_category_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_process_category_name ON dict_process_category USING btree (category_name, id);


--
-- Name: idx_dict_process_detail_category_enabled_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_process_detail_category_enabled_sort ON dict_process_detail USING btree (category_id, enabled, sort_no, id);


--
-- Name: idx_dict_process_detail_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_process_detail_name ON dict_process_detail USING btree (detail_name, id);


--
-- Name: idx_item_quote_version_material_no_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_material_no_id ON item_quote_version USING btree (material_no, id);


--
-- Name: idx_item_quote_version_material_source_ticket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_material_source_ticket ON item_quote_version USING btree (material_no_source_ticket_id) WHERE (material_no_source_ticket_id IS NOT NULL);


--
-- Name: idx_item_quote_version_material_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_material_status ON item_quote_version USING btree (material_no_status, rfq_id, id);


--
-- Name: idx_item_quote_version_repeat_released_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_repeat_released_key ON item_quote_version USING btree (part_no, version, id DESC) WHERE (((status)::text = '已提单'::text) AND (tax_included_unit_price IS NOT NULL) AND (material_data IS NOT NULL) AND (process_route IS NOT NULL));


--
-- Name: idx_item_quote_version_rfq_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_rfq_id ON item_quote_version USING btree (rfq_id, id);


--
-- Name: idx_item_quote_version_rfq_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_rfq_id_id ON item_quote_version USING btree (rfq_id, id);


--
-- Name: idx_item_quote_version_rfq_part_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_rfq_part_version ON item_quote_version USING btree (rfq_id, part_no, version);


--
-- Name: idx_item_quote_version_rfq_part_version_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_rfq_part_version_id ON item_quote_version USING btree (rfq_id, part_no, version, id);


--
-- Name: idx_item_quote_version_rfq_status_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_rfq_status_id ON item_quote_version USING btree (rfq_id, status, id);


--
-- Name: idx_item_quote_version_sales_order_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_quote_version_sales_order_record ON item_quote_version USING btree (sales_order_import_record_id);


--
-- Name: idx_order_delivery_ticket_active_flow_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_active_flow_id ON order_delivery_ticket USING btree (id DESC) WHERE ((NULLIF(btrim((material_no)::text), ''::text) IS NULL) OR ((COALESCE(mes_sync_status, 'PENDING'::character varying))::text <> 'SUCCESS'::text) OR ((COALESCE(wms_allocate_status, 'PENDING'::character varying))::text <> 'SUCCESS'::text));


--
-- Name: idx_order_delivery_ticket_completed_flow_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_completed_flow_id ON order_delivery_ticket USING btree (mes_sync_status, wms_allocate_status, id DESC) WHERE (NULLIF(btrim((material_no)::text), ''::text) IS NOT NULL);


--
-- Name: idx_order_delivery_ticket_delivery_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_delivery_date ON order_delivery_ticket USING btree (delivery_date, id);


--
-- Name: idx_order_delivery_ticket_internal_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_internal_project ON order_delivery_ticket USING btree (internal_project_no, id);


--
-- Name: idx_order_delivery_ticket_material_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_material_no ON order_delivery_ticket USING btree (material_no, id);


--
-- Name: idx_order_delivery_ticket_mes_external_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_mes_external_task ON order_delivery_ticket USING btree (mes_external_task_id, id) WHERE ((mes_external_task_id IS NOT NULL) AND (TRIM(BOTH FROM mes_external_task_id) <> ''::text));


--
-- Name: idx_order_delivery_ticket_mes_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_mes_status ON order_delivery_ticket USING btree (mes_sync_status, id);


--
-- Name: idx_order_delivery_ticket_part_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_part_version ON order_delivery_ticket USING btree (part_no, version, id);


--
-- Name: idx_order_delivery_ticket_rfq_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_rfq_id_id ON order_delivery_ticket USING btree (rfq_id, id);


--
-- Name: idx_order_delivery_ticket_rfq_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_rfq_item_id ON order_delivery_ticket USING btree (rfq_id, item_id);


--
-- Name: idx_order_delivery_ticket_rfq_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_rfq_no ON order_delivery_ticket USING btree (rfq_no, id);


--
-- Name: idx_order_delivery_ticket_rfq_part_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_rfq_part_version ON order_delivery_ticket USING btree (rfq_id, part_no, version);


--
-- Name: idx_order_delivery_ticket_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_source ON order_delivery_ticket USING btree (source_type, source_order_id, source_item_id);


--
-- Name: idx_order_delivery_ticket_wms_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_delivery_ticket_wms_status ON order_delivery_ticket USING btree (wms_allocate_status, id);


--
-- Name: idx_order_rfq_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_customer ON order_rfq USING btree (customer_name);


--
-- Name: idx_order_rfq_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_customer_id ON order_rfq USING btree (customer_name, id DESC);


--
-- Name: idx_order_rfq_customer_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_customer_order_no ON order_rfq USING btree (customer_order_no);


--
-- Name: idx_order_rfq_customer_status_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_customer_status_id ON order_rfq USING btree (customer_name, status, id DESC);


--
-- Name: idx_order_rfq_owner_dept; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_owner_dept ON order_rfq USING btree (owner_dept_id);


--
-- Name: idx_order_rfq_owner_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_owner_user ON order_rfq USING btree (owner_user_id);


--
-- Name: idx_order_rfq_source_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_source_status_created ON order_rfq USING btree (source_type, status, created_at);


--
-- Name: idx_order_rfq_status_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_rfq_status_id ON order_rfq USING btree (status, id DESC);


--
-- Name: idx_sales_order_header_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_header_customer ON sales_order_header USING btree (customer_name, id);


--
-- Name: idx_sales_order_header_customer_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_header_customer_order_no ON sales_order_header USING btree (customer_order_no);


--
-- Name: idx_sales_order_header_source_type_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_header_source_type_created ON sales_order_header USING btree (source_type, created_at DESC);


--
-- Name: idx_sales_order_header_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_header_status_created ON sales_order_header USING btree (status, created_at DESC);


--
-- Name: idx_sales_order_import_record_batch_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_import_record_batch_id ON sales_order_import_record USING btree (batch_id);


--
-- Name: idx_sales_order_import_record_customer_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_import_record_customer_order_no ON sales_order_import_record USING btree (customer_order_no);


--
-- Name: idx_sales_order_import_record_sales_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_import_record_sales_order_id ON sales_order_import_record USING btree (sales_order_id);


--
-- Name: idx_sales_order_import_record_u9_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_import_record_u9_code ON sales_order_import_record USING btree (u9_code);


--
-- Name: idx_sales_order_item_assembly_child; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_assembly_child ON sales_order_item_assembly_relation USING btree (child_item_id);


--
-- Name: idx_sales_order_item_assembly_sales_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_assembly_sales_order ON sales_order_item_assembly_relation USING btree (sales_order_id, parent_item_id, sort_no, id);


--
-- Name: idx_sales_order_item_material_no_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_material_no_id ON sales_order_item USING btree (material_no, id);


--
-- Name: idx_sales_order_item_material_source_ticket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_material_source_ticket ON sales_order_item USING btree (material_no_source_ticket_id) WHERE (material_no_source_ticket_id IS NOT NULL);


--
-- Name: idx_sales_order_item_material_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_material_status ON sales_order_item USING btree (material_no_status, sales_order_id, id);


--
-- Name: idx_sales_order_item_order_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_order_id_id ON sales_order_item USING btree (sales_order_id, id);


--
-- Name: idx_sales_order_item_part_version_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_part_version_id ON sales_order_item USING btree (part_no, version, id);


--
-- Name: idx_sales_order_item_source_order_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_source_order_item ON sales_order_item USING btree (source_type, source_order_id, source_item_id, id);


--
-- Name: idx_sales_order_item_status_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_item_status_id ON sales_order_item USING btree (status, id);


--
-- Name: idx_sales_order_source_rfq_rfq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_source_rfq_rfq ON sales_order_source_rfq USING btree (rfq_id, sales_order_id);


--
-- Name: idx_sales_order_source_rfq_sales_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_order_source_rfq_sales_order ON sales_order_source_rfq USING btree (sales_order_id, rfq_id);


--
-- Name: idx_system_data_center_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_data_center_enabled ON system_data_center_config USING btree (enabled);


--
-- Name: idx_system_data_center_endpoint_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_data_center_endpoint_target ON system_data_center_endpoint_config USING btree (target_system_code);


--
-- Name: idx_system_external_integration_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_external_integration_enabled ON system_external_integration_config USING btree (enabled);


--
-- Name: idx_system_external_integration_system; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_external_integration_system ON system_external_integration_config USING btree (system_code);


--
-- Name: idx_system_file_object_biz; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_file_object_biz ON system_file_object USING btree (biz_type, biz_id);


--
-- Name: idx_system_file_object_object_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_file_object_object_key ON system_file_object USING btree (object_key);


--
-- Name: idx_system_file_object_storage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_file_object_storage ON system_file_object USING btree (storage_type, storage_config_id);


--
-- Name: idx_system_file_storage_config_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_file_storage_config_enabled ON system_file_storage_config USING btree (enabled);


--
-- Name: idx_system_file_storage_config_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_file_storage_config_type ON system_file_storage_config USING btree (storage_type);


--
-- Name: idx_system_inbound_callback_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_inbound_callback_enabled ON system_inbound_callback_config USING btree (enabled);


--
-- Name: idx_system_inbound_callback_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_inbound_callback_source ON system_inbound_callback_config USING btree (source_system_code);


--
-- Name: idx_system_permission_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_permission_parent ON system_permission USING btree (parent_id);


--
-- Name: idx_system_permission_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_permission_type ON system_permission USING btree (permission_type);


--
-- Name: idx_system_task_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_task_status_created ON system_task USING btree (status, created_at DESC);


--
-- Name: idx_system_task_type_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_task_type_created ON system_task USING btree (task_type, created_at DESC);


--
-- Name: idx_system_user_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_user_deleted ON system_user USING btree (deleted);


--
-- Name: idx_system_user_dept; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_user_dept ON system_user USING btree (dept_id);


--
-- Name: idx_system_user_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_user_enabled ON system_user USING btree (enabled);


--
-- Name: uk_delivery_allocation_mes_production_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_delivery_allocation_mes_production_request ON order_delivery_ticket_allocation USING btree (mes_production_request_id) WHERE ((mes_production_request_id IS NOT NULL) AND (TRIM(BOTH FROM mes_production_request_id) <> ''::text));


--
-- Name: uk_delivery_mes_request_active_dedup; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_delivery_mes_request_active_dedup ON order_delivery_ticket_mes_request USING btree (dedup_key) WHERE (((request_status)::text = ANY ((ARRAY['PROCESSING'::character varying, 'CALLBACK_PENDING'::character varying])::text[])) AND (dedup_key IS NOT NULL) AND (TRIM(BOTH FROM dedup_key) <> ''::text));


--
-- Name: uk_delivery_mes_request_request_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_delivery_mes_request_request_id ON order_delivery_ticket_mes_request USING btree (mes_request_id);


--
-- Name: uk_delivery_mes_waiter_active_ticket; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_delivery_mes_waiter_active_ticket ON order_delivery_ticket_mes_waiter USING btree (waiter_ticket_id) WHERE ((wait_status)::text = ANY ((ARRAY['WAITING'::character varying, 'CALLBACK_PENDING'::character varying])::text[]));


--
-- Name: uk_order_delivery_ticket_mes_request_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_order_delivery_ticket_mes_request_id ON order_delivery_ticket USING btree (mes_request_id) WHERE ((mes_request_id IS NOT NULL) AND (TRIM(BOTH FROM mes_request_id) <> ''::text));


--
-- Name: uk_sales_order_header_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sales_order_header_no ON sales_order_header USING btree (sales_order_no);


--
-- Name: uk_sales_order_header_release_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sales_order_header_release_batch ON sales_order_header USING btree (source_release_batch_key) WHERE (source_release_batch_key IS NOT NULL);


--
-- Name: uk_sales_order_item_assembly_parent_child; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sales_order_item_assembly_parent_child ON sales_order_item_assembly_relation USING btree (parent_item_id, child_item_id);


--
-- Name: uk_sales_order_item_source_ticket; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_sales_order_item_source_ticket ON sales_order_item USING btree (source_ticket_id) WHERE (source_ticket_id IS NOT NULL);


--
-- Name: uk_system_data_center_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_system_data_center_active ON system_data_center_config USING btree (active) WHERE (active = true);


--
-- Name: uk_system_external_integration_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_system_external_integration_active ON system_external_integration_config USING btree (system_code, active) WHERE (active = true);


--
-- Name: uk_system_file_object_public_path; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_system_file_object_public_path ON system_file_object USING btree (public_path);


--
-- Name: uk_system_file_storage_config_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_system_file_storage_config_active ON system_file_storage_config USING btree (active) WHERE ((active = true) AND (enabled = true));


--
-- Name: dict_material dict_material_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_material
    ADD CONSTRAINT fk_dict_material_type FOREIGN KEY (material_type_id) REFERENCES dict_material_type(id) ON DELETE RESTRICT;


--
-- Name: dict_process_detail dict_process_detail_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dict_process_detail
    ADD CONSTRAINT dict_process_detail_category_id_fkey FOREIGN KEY (category_id) REFERENCES dict_process_category(id) ON DELETE RESTRICT;


--
-- Name: sales_order_item fk_sales_order_item_source_ticket; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item
    ADD CONSTRAINT fk_sales_order_item_source_ticket FOREIGN KEY (source_ticket_id) REFERENCES order_delivery_ticket(id);


--
-- Name: item_assembly_relation item_assembly_relation_child_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_assembly_relation
    ADD CONSTRAINT item_assembly_relation_child_item_id_fkey FOREIGN KEY (child_item_id) REFERENCES item_quote_version(id) ON DELETE CASCADE;


--
-- Name: item_assembly_relation item_assembly_relation_parent_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_assembly_relation
    ADD CONSTRAINT item_assembly_relation_parent_item_id_fkey FOREIGN KEY (parent_item_id) REFERENCES item_quote_version(id) ON DELETE CASCADE;


--
-- Name: item_assembly_relation item_assembly_relation_rfq_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_assembly_relation
    ADD CONSTRAINT item_assembly_relation_rfq_id_fkey FOREIGN KEY (rfq_id) REFERENCES order_rfq(id) ON DELETE CASCADE;


--
-- Name: item_quote_version item_quote_version_rfq_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY item_quote_version
    ADD CONSTRAINT item_quote_version_rfq_id_fkey FOREIGN KEY (rfq_id) REFERENCES order_rfq(id);


--
-- Name: order_delivery_ticket_allocation order_delivery_ticket_allocation_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_allocation
    ADD CONSTRAINT order_delivery_ticket_allocation_item_id_fkey FOREIGN KEY (item_id) REFERENCES item_quote_version(id);


--
-- Name: order_delivery_ticket_allocation order_delivery_ticket_allocation_rfq_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_allocation
    ADD CONSTRAINT order_delivery_ticket_allocation_rfq_id_fkey FOREIGN KEY (rfq_id) REFERENCES order_rfq(id);


--
-- Name: order_delivery_ticket_allocation order_delivery_ticket_allocation_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_allocation
    ADD CONSTRAINT order_delivery_ticket_allocation_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES order_delivery_ticket(id);


--
-- Name: order_delivery_ticket order_delivery_ticket_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket
    ADD CONSTRAINT order_delivery_ticket_item_id_fkey FOREIGN KEY (item_id) REFERENCES item_quote_version(id);


--
-- Name: order_delivery_ticket_mes_request order_delivery_ticket_mes_request_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_request
    ADD CONSTRAINT order_delivery_ticket_mes_request_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES order_delivery_ticket(id);


--
-- Name: order_delivery_ticket_mes_waiter order_delivery_ticket_mes_waiter_master_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_waiter
    ADD CONSTRAINT order_delivery_ticket_mes_waiter_master_ticket_id_fkey FOREIGN KEY (master_ticket_id) REFERENCES order_delivery_ticket(id);


--
-- Name: order_delivery_ticket_mes_waiter order_delivery_ticket_mes_waiter_waiter_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket_mes_waiter
    ADD CONSTRAINT order_delivery_ticket_mes_waiter_waiter_ticket_id_fkey FOREIGN KEY (waiter_ticket_id) REFERENCES order_delivery_ticket(id);


--
-- Name: order_delivery_ticket order_delivery_ticket_rfq_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY order_delivery_ticket
    ADD CONSTRAINT order_delivery_ticket_rfq_id_fkey FOREIGN KEY (rfq_id) REFERENCES order_rfq(id);


--
-- Name: sales_order_import_record sales_order_import_record_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_import_record
    ADD CONSTRAINT sales_order_import_record_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES sales_order_import_batch(id) ON DELETE CASCADE;


--
-- Name: sales_order_item_assembly_relation sales_order_item_assembly_relation_child_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item_assembly_relation
    ADD CONSTRAINT sales_order_item_assembly_relation_child_item_id_fkey FOREIGN KEY (child_item_id) REFERENCES sales_order_item(id) ON DELETE CASCADE;


--
-- Name: sales_order_item_assembly_relation sales_order_item_assembly_relation_parent_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item_assembly_relation
    ADD CONSTRAINT sales_order_item_assembly_relation_parent_item_id_fkey FOREIGN KEY (parent_item_id) REFERENCES sales_order_item(id) ON DELETE CASCADE;


--
-- Name: sales_order_item sales_order_item_sales_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_item
    ADD CONSTRAINT sales_order_item_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES sales_order_header(id) ON DELETE CASCADE;


--
-- Name: sales_order_source_rfq sales_order_source_rfq_rfq_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_source_rfq
    ADD CONSTRAINT sales_order_source_rfq_rfq_id_fkey FOREIGN KEY (rfq_id) REFERENCES order_rfq(id);


--
-- Name: sales_order_source_rfq sales_order_source_rfq_sales_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sales_order_source_rfq
    ADD CONSTRAINT sales_order_source_rfq_sales_order_id_fkey FOREIGN KEY (sales_order_id) REFERENCES sales_order_header(id) ON DELETE CASCADE;


--
-- Name: system_file_object system_file_object_storage_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_file_object
    ADD CONSTRAINT system_file_object_storage_config_id_fkey FOREIGN KEY (storage_config_id) REFERENCES system_file_storage_config(id) ON DELETE SET NULL;


--
-- Name: system_role_data_scope_customer system_role_data_scope_customer_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_data_scope_customer
    ADD CONSTRAINT system_role_data_scope_customer_role_id_fkey FOREIGN KEY (role_id) REFERENCES system_role(id) ON DELETE CASCADE;


--
-- Name: system_role_data_scope_dept system_role_data_scope_dept_dept_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_data_scope_dept
    ADD CONSTRAINT system_role_data_scope_dept_dept_id_fkey FOREIGN KEY (dept_id) REFERENCES system_dept(id) ON DELETE CASCADE;


--
-- Name: system_role_data_scope_dept system_role_data_scope_dept_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_data_scope_dept
    ADD CONSTRAINT system_role_data_scope_dept_role_id_fkey FOREIGN KEY (role_id) REFERENCES system_role(id) ON DELETE CASCADE;


--
-- Name: system_role_permission system_role_permission_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_permission
    ADD CONSTRAINT system_role_permission_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES system_permission(id) ON DELETE CASCADE;


--
-- Name: system_role_permission system_role_permission_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_role_permission
    ADD CONSTRAINT system_role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES system_role(id) ON DELETE CASCADE;


--
-- Name: system_user system_user_dept_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user
    ADD CONSTRAINT system_user_dept_id_fkey FOREIGN KEY (dept_id) REFERENCES system_dept(id);


--
-- Name: system_user system_user_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user
    ADD CONSTRAINT system_user_post_id_fkey FOREIGN KEY (post_id) REFERENCES system_post(id);


--
-- Name: system_user_role system_user_role_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user_role
    ADD CONSTRAINT system_user_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES system_role(id) ON DELETE CASCADE;


--
-- Name: system_user_role system_user_role_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_user_role
    ADD CONSTRAINT system_user_role_user_id_fkey FOREIGN KEY (user_id) REFERENCES system_user(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
