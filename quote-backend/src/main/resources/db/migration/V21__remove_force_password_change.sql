-- QPO no longer forces users to change password after account creation or reset.
-- Keep password_changed_at and lock fields for audit / security history, but remove the obsolete gate flag.
ALTER TABLE erp_user DROP COLUMN IF EXISTS force_password_change;
ALTER TABLE system_user DROP COLUMN IF EXISTS force_password_change;
