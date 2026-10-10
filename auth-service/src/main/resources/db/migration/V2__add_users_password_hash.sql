-- Credential storage for email-and-password login.
--
-- The column is intentionally nullable: every user created without password login keeps
-- working unchanged, and rows without a hash simply cannot authenticate with a
-- password until one is set. The column only ever stores an adaptive password
-- hash (BCrypt); raw passwords are never persisted anywhere.
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100);
