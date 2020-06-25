CREATE TABLE IF NOT EXISTS "alarm-history"
("id" SERIAL PRIMARY KEY, /* changed to BIGSERIAL/BIGINT in 025 */
"timestamp" TIMESTAMP NULL,
"income-msg" VARCHAR(1024) NOT NULL,
"alarm-id" bigint NULL, /* alarm code */
"alarm-info-id" bigint NULL, /* alarm info 1 */
"device-id" bigint NULL);
