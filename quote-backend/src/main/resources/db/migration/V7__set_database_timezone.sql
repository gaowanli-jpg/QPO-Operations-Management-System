SET TIME ZONE 'Asia/Shanghai';

DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone = %L', current_database(), 'Asia/Shanghai');
END $$;
