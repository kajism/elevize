CREATE TABLE "stock" (
"id" SERIAL PRIMARY KEY,
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
"item-name" VARCHAR(256) NOT NULL,
"delta-pcs" SMALLINT NOT NULL,
"note" VARCHAR(256) NULL
);
