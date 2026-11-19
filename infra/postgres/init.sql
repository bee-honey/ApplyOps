-- Temporal creates its own databases (temporal, temporal_visibility) as the superuser.
-- ApplyOps application data lives in a separate database and role.
CREATE ROLE applyops WITH LOGIN PASSWORD 'applyops';
CREATE DATABASE applyops OWNER applyops;
