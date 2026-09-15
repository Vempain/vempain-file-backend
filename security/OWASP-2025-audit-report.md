# Vempain File backend — OWASP Top 10:2025 audit

**Date:** 2026-09-15
**Scope:** `vempain-file-backend` `api/` and `service/` only. Frontend code was not
modified; the browser/API boundary is considered authenticated by the shared
`vempain-auth` dependency. Review used the task brief, root Top 10 playbook,
static inspection, and existing tests.

## Inventory and threat model

The HTTP API is rooted at `/api` and exposes file content, typed-file and file
group list/read/delete endpoints, scans, path completion, tags, location
guards, statistics, publishing/progress, and music/GPS dataset publication.
These endpoints are implemented by `*API` interfaces under
`api/src/main/java/.../rest` and controllers under
`service/src/main/java/.../controller`. No endpoint is intentionally anonymous;
authentication and the administrator policy are supplied by the shared auth
security configuration. The management server is on port 8081 and exposes
health/info by default; Swagger is disabled in `prod.yaml`.

Trust boundaries are browser → Spring Security/API, API → PostgreSQL/Flyway,
API → configured original/export filesystem and `exiftool`, API → Admin
OpenFeign/JWT, and CI → GitHub Packages/container registry. Sensitive data
includes JWTs, Admin credentials, GPS coordinates/location guards, file
metadata, and filesystem contents. Runtime roots and credentials are
configuration values; `SetupVerification` fails closed on missing
`override-me` values and validates production Admin HTTPS and datasource TLS.

Anonymous callers should receive 401; a non-admin authenticated caller must be
rejected by the shared policy; an administrator can invoke the API. A stolen
Admin service token can publish to Admin until its expiry. The backend has no
user-controlled outbound URL fetch; Admin is configured server-side.

## A01–A10 coverage matrix

| Category                               | Checklist verdict                      | Evidence / finding                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|----------------------------------------|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A01 Broken Access Control              | **PASS for boundary; MEDIUM residual** | Shared auth is the single API security boundary and no anonymous route is declared here. File content, scan, completion, DB IDs, publish, tag and guard operations are administrator-only by deployment policy. Filesystem paths now use real-path containment and reject symlink escapes (`FileContentService`, `FileScannerService`, `PathCompletionService`). Fine-grained per-user ACLs and access-failure alerting are not implemented in this repository. |
| A02 Security Misconfiguration          | **PASS / verify deployment**           | `application.yaml` uses JPA `validate`, disables SQL logging, exposes only health/info, and no longer enables the test flag. `prod.yaml` disables Swagger. `SetupVerification` rejects placeholder configuration and production non-HTTPS/non-TLS transport. Deployment must still verify management-port network isolation and headers.                                                                                                                        |
| A03 Software Supply Chain Failures     | **INFO / UNVERIFIED**                  | Gradle dependencies and GitHub Actions workflow were inventoried. No SCA, SBOM, artifact-signing, or immutable-action scanner exists; none was added because the task forbids silently adding tooling. Pin actions, generate SBOMs, and scan dependencies in CI.                                                                                                                                                                                                |
| A04 Cryptographic Failures             | **PASS with residual**                 | JWT default lifetime is 900000 ms; production rejects non-HTTPS Admin and datasource URLs lacking TLS mode. Certificate verification (`verify-full`) and shared-auth key rotation remain deployment work.                                                                                                                                                                                                                                                       |
| A05 Injection                          | **PASS**                               | Repository searches use parameter binding and allow-listed sort fields (`FileSearchHelper`, `FileGroupRepositoryImpl`). ExifTool arguments are built as argument lists, not shell strings. Scan and completion paths are normalized and contained.                                                                                                                                                                                                              |
| A06 Insecure Design                    | **MEDIUM residual**                    | Expensive scans, metadata extraction, thumbnail/video jobs, and dataset publication have no per-user rate/size budget. GPS publication applies the existing guarded-location policy in `DataService`; operational quotas need product capacity decisions.                                                                                                                                                                                                       |
| A07 Authentication Failures            | **MEDIUM residual**                    | Authentication is delegated to shared auth; this service does not implement logout, refresh rotation, or token revocation. Access-token lifetime is bounded to 15 minutes in the default configuration, but stolen tokens remain usable until expiry.                                                                                                                                                                                                           |
| A08 Software/Data Integrity Failures   | **INFO / UNVERIFIED**                  | No Java native deserialization or unrestricted Jackson default typing was found. CI provenance, signed artifacts, and dependency lock/provenance checks need platform ownership.                                                                                                                                                                                                                                                                                |
| A09 Security Logging & Alerting        | **MEDIUM residual**                    | Errors have correlation IDs and sensitive JWT/request payload logging was avoided. Security-event aggregation, retention, repeated-denial alerting, and production log redaction require deployment verification.                                                                                                                                                                                                                                               |
| A10 Mishandling Exceptional Conditions | **PASS**                               | `GlobalExceptionHandler` returns generic ProblemDetail responses with correlation IDs; downstream bodies and filesystem paths are not returned. Filesystem failures are mapped to generic statuses.                                                                                                                                                                                                                                                             |

## Remediated findings

### F-01 — HIGH — filesystem symlink/path escape (A01/A05)

`FileContentService` previously checked only normalized lexical paths. A
database path component or symlink under the configured root could resolve to a
file outside that root. Scan roots and path completion had the same weakness.
The services now resolve configured roots and candidates with `toRealPath()`,
require real-path containment, reject symlink scan/completion entries, and
return generic errors. Regression tests:
`FileContentServiceUTC.resolveOriginalFile_rejectsSymlinkOutsideRoot` and
`FileScannerServiceUTC.rejectsScanDirectoryThroughSymlink`.

### F-02 — MEDIUM — long-lived default access token (A04/A07)

The default `vempain.app.jwt-expiration-ms` was one day. It is now 900000 ms
(15 minutes), reducing replay exposure while retaining shared-auth behavior.
The shared auth service still owns revocation and refresh-token rotation.

## Accepted risks and deferred recommendations

* Implement ownership-scoped repository queries if non-admin File users are
  introduced; do not rely on UI guards.
* Add request/scan/upload limits and rate limiting after capacity requirements
  are agreed.
* Add shared-auth refresh rotation, revocation, secure browser token storage,
  key rotation, and production cookie/header verification.
* Add CI SCA/SBOM/signature verification with pinned immutable action SHAs.
* Verify CSP/HSTS/Permissions-Policy, management-port isolation, TLS
  certificate verification, centralized alerting, and log retention in each
  deployment.

## Verification

Targeted checks:

```text
./gradlew :service:test --tests '*FileContentServiceUTC' --tests '*FileScannerServiceUTC'
```

Full existing check (requires configured GitHub Package credentials, Docker
for Testcontainers, and `exiftool`):

```text
./gradlew clean test
```

Frontend audit/report is intentionally not changed in this backend task; it
must be reviewed together with this API report as required by the root task.
