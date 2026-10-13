---
name: add-flyway-migration
description: Add a Flyway migration to the file backend (new column, index, backfill) and keep entities, native SQL and tests in step. Use when a schema or data change is needed.
---

# Add a Flyway migration to vempain-file-backend

- Migrations live in `service/src/main/resources/db/migration/` and are numbered `V10xx__snake_case.sql`; take the next free number (the auth tables come from
  the `vempain-auth-core` jar under `db/migration/auth` and never collide with these numbers).
- JPA runs with `ddl-auto: validate`, so every column you add must also appear on the entity (`service/.../entity/`) with the exact
  type and nullability; typed files use JOINED inheritance (`files` + `<type>_files`).
- Native SQL (`FileGroupRepositoryImpl`, `MetadataRepositoryImpl`) names columns literally; grep for the old column name after a rename.
- Data backfills belong in the migration, not in application startup code (see `V1011__file_descriptions.sql`: fill from the `metadata`
  table, idempotent `WHERE` guards). Prefer `UPDATE ... FROM` with explicit conditions over procedural code.
- Indexes: a btree on a `TEXT` column with large values fails; index the selective short columns only (`V1010`).
- Test rows in `*CTC`/`*ITC` are inserted with `jdbcTemplate` using `OVERRIDING SYSTEM VALUE` for explicit ids >= 9001; clean up with
  `deleteFileRow`.
- Verify by running one Testcontainers test class (`./gradlew :service:test --tests '*ControllerCTC'`): Flyway applies the migration on an
  empty database, and `validate` fails fast on entity mismatches.
- Mention the migration in `AGENTS.md` when it changes documented behaviour.
