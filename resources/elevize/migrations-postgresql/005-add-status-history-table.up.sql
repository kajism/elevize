CREATE TABLE IF NOT EXISTS "status-history"
("id" SERIAL PRIMARY KEY, /* changed to BIGSERIAL/BIGINT in 025 */
"timestamp" TIMESTAMP NOT NULL,
"variable-id" BIGINT NOT NULL,
"value" VARCHAR(512));

-- psql -h localhost -U elevize elevize
-- select count(*) from "status-history" where "timestamp" < TIMESTAMP '2019-09-01T00:00:00';
-- delete from "status-history" where "timestamp" < TIMESTAMP '2019-09-01T00:00:00';
-- cluster "status-history" using "status-history-timestamp-idx";
