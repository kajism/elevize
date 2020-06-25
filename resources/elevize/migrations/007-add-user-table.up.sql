CREATE TABLE "user"
("id" bigint auto_increment PRIMARY KEY,
"title" VARCHAR(30) NOT NULL,
"login" VARCHAR (128) NOT NULL,
"passwd" VARCHAR (128),
"email" VARCHAR (128),
"roles" VARCHAR (128),
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
"modified" TIMESTAMP NOT NULL AS CURRENT_TIMESTAMP());

-- admin/123
INSERT INTO "user" VALUES (null, 'Administrator', 'admin',
'$s0$f0801$ZooqiFNpuUgF+uG4sxxFlQ==$nY5A3RWCS74+ZJfNSArarKgdyKaK0Prd3itMQUuUxQQ=',
null, 'admin', current_timestamp(), current_timestamp());

