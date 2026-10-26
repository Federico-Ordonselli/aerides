-- The audit log is an append-only historical record: it must survive the deletion of the group
-- or user it refers to, and still say which one it was. A foreign key would either delete the
-- entries (CASCADE) or forget the id (SET NULL), so audit_entries keeps plain id columns instead.
ALTER TABLE audit_entries DROP CONSTRAINT audit_entries_group_id_fkey;
ALTER TABLE audit_entries DROP CONSTRAINT audit_entries_actor_id_fkey;
