---
name: add-rest-endpoint
description: Add or change a REST endpoint of the Vempain file backend (API interface in api/, controller and service in service/, security path, tests, CLI sync). Use when asked for a new endpoint, DTO, paged list or request/response field.
---

# Add a REST endpoint to vempain-file-backend

## Where things go

1. Contract in `api/src/main/java/fi/poltsi/vempain/file/rest/<Name>API.java` (a `BASE_PATH` constant, Swagger
   `@Operation`/`@ApiResponses`, `@SecurityRequirement(name = "Bearer Authentication")`, `@Valid @RequestBody`).
2. DTOs in `api/.../api/request` and `api/.../api/response`: Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`,
   `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` from `tools.jackson.databind`, `@Schema` on class and fields,
   Jakarta validation constraints. JSON is snake_case only. Types shared with the admin backend belong in `vempain-common`, never here.
3. Controller in `service/.../controller/<Name>Controller.java` implementing the interface; it only delegates to the service.
4. Service in `service/.../service/`; repository queries in `service/.../repository/` (Spring Data JPA, `JpaSpecificationExecutor`,
   native SQL only in `*RepositoryImpl` with bound parameters).
5. Register the path prefix in `service/.../security/WebSecurityConfig.configureApplicationAuthorization` (`authorization.authenticated(...)`).

## Paged lists

- Use `POST <BASE_PATH>/paged` with the shared `PagedRequest` (`fi.poltsi.vempain.auth.api.request`) and answer
  `PagedResponse.of(...)`. Clamp `size` to 1..200, map `sort_by` through an allow-list switch (see `FileSearchHelper.buildSort`), and
  build every `LIKE` from request text with `fi.poltsi.vempain.file.tools.LikePatterns` plus an explicit escape (`ESCAPE_CHAR` in
  Criteria API, `ESCAPE_CLAUSE` in native SQL). Filters that need more than one column get their own request DTO (`MetadataSearchRequest` is the template).
- Lists of files must be ACL filtered: wrap the specification in `fileAclService.readableFiles(spec)`.

## Errors

- `IllegalArgumentException` -> 400, `AccessDeniedException` -> 403, `EntityNotFoundException` -> 404 (`GlobalExceptionHandler`).
  Validate and authorize synchronously in the service before any side effect.

## Tests (mandatory)

- `*CTC` extending `AbstractControllerCTC` (`doPost/doGet`, `seedFileRow`/`seedFileRowWithAcl`, Testcontainers). Seeded files need
  their subtype row too (`image_files`, `document_files`, ...) or JPA cannot load them. Cover a granted and a denied ACL case.
- Add the endpoint to `RestEndpointsCTC` (anonymous -> 4xx, authenticated -> never 5xx).
- `*UTC` for the service with Mockito; `RequestContractJTC`/`ResponseContractJTC` when the DTO is a shared contract.
- Run `./gradlew :service:test --tests '*<Name>*'` and report the result.

## Finish

- Document the endpoint in `AGENTS.md` and, if user facing, `README.md`; update `security/OWASP-2025-audit-report.md` when the change
  touches authorization, query building or filesystem handling.
- Review `vempain-cli`: every API change needs a matching command/test update there (or an explicit note that the CLI does not cover it).
