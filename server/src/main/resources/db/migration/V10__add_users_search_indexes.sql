CREATE INDEX idx_users_search_full_name ON users (`status`, full_name, user_id);
CREATE INDEX idx_users_search_reg_number ON users (`status`, reg_number, user_id);
CREATE INDEX idx_users_search_rank ON users (`status`, `rank`, user_id);
