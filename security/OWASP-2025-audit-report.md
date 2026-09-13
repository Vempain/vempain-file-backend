# OWASP Top 10:2025 Audit - Vempain File

Date: 2026-09-12 · Scope: `vempain-file-backend`, `vempain-file-frontend`, shared auth security, and File deployment
configuration · Method: static review, targeted tests, and existing local build validation

## Executive summary

The audit found two high-impact data/authorization weaknesses and five medium-impact hardening weaknesses. File
operations are now restricted to the administrator authority at the shared security boundary, GPS datasets exclude
guarded coordinates, scan paths are contained under configured roots, production management exposure is narrowed,
production transport settings fail closed, sensitive logs are removed, and client errors are sanitized. JWT
revocation and refresh-token rotation remain a deferred shared-auth design item and must be addressed before treating
stolen access tokens as fully revocable.

## Coverage matrix

| Category                                 | Status             | Evidence                                                                                                                                                                                            |
|------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A01 Broken Access Control                | PARTIALLY FIXED    | Shared `WebSecurityConfig` restricts File reads and mutations to `ROLE_ADMIN`; GPS publication also enforces privacy filtering. Fine-grained per-object ACL enforcement remains a follow-up.        |
| A02 Security Misconfiguration            | FIXED / UNVERIFIED | No local default profile; actuator exposure is limited in `application.yaml`; production profile still requires deployment smoke verification.                                                      |
| A03 Software Supply Chain Failures       | UNVERIFIED         | Lockfile/immutable install and CI references were reviewed; no dependency vulnerability scanner was configured or added.                                                                            |
| A04 Cryptographic Failures               | PARTIALLY FIXED    | Compose uses required PostgreSQL TLS, production rejects non-TLS datasource/Admin URLs, and JWT lifetime is 15 minutes. Certificate verification and token revocation require deployment follow-up. |
| A05 Injection                            | FIXED / PASS       | Scan-directory traversal is rejected before filesystem access; existing repository query allow-lists and parameter binding remain intact.                                                           |
| A06 Insecure Design                      | PARTIALLY FIXED    | Guarded GPS locations cannot enter published datasets; unbounded operational workflows and fine-grained ACL semantics need a separate design pass.                                                  |
| A07 Authentication Failures              | DEFERRED           | Access-token lifetime is reduced to 15 minutes, but browser local-storage handling and server-side revocation/refresh rotation remain.                                                              |
| A08 Software/Data Integrity Failures     | UNVERIFIED         | No native Java deserialization or unrestricted Jackson default typing was found; artifact signing and dependency provenance need CI review.                                                         |
| A09 Security Logging & Alerting Failures | PARTIALLY FIXED    | JWT, login-response, configuration-value, and full ingest-request logging were removed. Central alerting and complete security-event audit coverage remain unverified.                              |
| A10 Exceptional Conditions               | FIXED              | Error responses are generic `ProblemDetail` responses with correlation IDs; filesystem and downstream response bodies are no longer returned.                                                       |

## Findings

### [HIGH] F-01 - File operations were available to every authenticated user · A01:2025 · CWE-862/CWE-639

- **Location:** `vempain-auth/core/src/main/java/fi/poltsi/vempain/auth/security/WebSecurityConfig.java`
- **Description:** The previous policy required authentication but did not distinguish administrator operations from ordinary
  users. File reads, deletes, scans, tags, groups, location guards, and publishing were reachable after login.
- **Impact:** A low-privilege authenticated account could access or mutate shared file-management data.
- **Fix applied:** Added centralized HTTP-method/path authorization requiring `ROLE_ADMIN` for File resources, scans,
  publication, data publication, group/tag mutation, and location-guard mutation. Existing controller tests now use an
  administrator principal for authorized behavior.
- **Verification:** `RestEndpointsCTC` continues to verify anonymous rejection; full backend test suite passes.
- **Residual risk / follow-up:** Fine-grained ACL checks for per-object ownership are not yet implemented. The current
  safe default is administrator-only access rather than a user-scoped ACL query.

### [HIGH] F-02 - Guarded GPS coordinates were published in datasets · A01/A06:2025 · CWE-359

- **Location:** `service/src/main/java/fi/poltsi/vempain/file/service/DataService.java`
- **Description:** GPS CSV generation previously exported every repository result without applying the existing
  `LocationService.isGuardedLocation` policy used by normal file publishing.
- **Impact:** A caller could publish coordinates from privacy-guarded locations to Vempain Admin.
- **Fix applied:** Directory and file-group dataset generation now filters guarded GPS locations before CSV creation and
  returns a generic not-found result when no publishable locations remain.
- **Verification:** `DataServiceUTC.generateAndPublishGpsTimeSeriesByFileGroup_excludesGuardedLocations`.

### [MEDIUM] F-03 - Local defaults and management endpoints were exposed · A02:2025

- **Location:** `service/src/main/resources/application.yaml`,
  `vempain-auth/core/src/main/java/fi/poltsi/vempain/auth/security/WebSecurityConfig.java`
- **Description:** The File service selected `local` by default and exposed all actuator endpoints and API documentation
  under that profile.
- **Fix applied:** Removed the local default profile, limited default actuator exposure to `health`, `info`, and
  production management exposure is denied by the shared security policy.
- **Verification:** Full backend and shared-auth test suites pass. A production smoke test remains required in deployment.

### [MEDIUM] F-04 - Service and database transport could be plaintext · A04/A07:2025 · CWE-319

- **Location:** root `docker-compose.yaml`, `vempain-cluster/docker-compose.yaml`, and
  `service/src/main/java/fi/poltsi/vempain/file/SetupVerification.java`
- **Description:** Deployment defaults previously used `useSSL=false`, an HTTP Admin URL, and a long-lived JWT.
- **Fix applied:** Compose now requires PostgreSQL TLS, obtains the Admin URL and JWT secret from required environment
  variables, limits JWT lifetime to 900000 ms, and production startup rejects non-HTTPS Admin URLs or non-TLS datasource
  URLs. `ctrl_compose.sh` fails closed when required deployment variables are absent.
- **Residual risk / follow-up:** PostgreSQL certificate verification (`sslmode=verify-full`) and internal Admin mTLS still
  require environment-specific certificate provisioning.

### [MEDIUM] F-05 - Sensitive values were logged · A09:2025 · CWE-532

- **Location:** `SetupVerification.java`, `VempainAdminTokenProvider.java`, and `PublishService.java`
- **Description:** Configuration values, login responses containing JWTs, and full ingest request payloads could be logged.
- **Fix applied:** Logs now contain only configuration keys, operation metadata, and non-sensitive identifiers; JWTs,
  passwords, GPS data, and complete request payloads are excluded.
- **Verification:** `VempainAdminTokenProviderUTC` and the full backend suite pass. Production log capture remains an
  operational verification item.

### [MEDIUM] F-06 - Filesystem traversal and internal error disclosure · A01/A10:2025 · CWE-22/CWE-209

- **Location:** `FileScannerService.java`, `DirectoryProcessorService.java`, `GlobalExceptionHandler.java`
- **Description:** Scan paths were resolved without containment verification, and error responses could expose absolute
  paths, downstream response bodies, or exception messages.
- **Fix applied:** Scan paths are normalized and required to remain under the configured root. File errors expose only a
  filename, downstream failures use generic reasons, and centralized `ProblemDetail` responses include a correlation ID
  without internal details.
- **Verification:** `FileScannerServiceUTC.rejectsScanDirectoryOutsideConfiguredRoot`, controller error-path tests, and
  the full backend suite pass.

## Accepted risks and deferred items

| Item                                                   | Category | Why deferred                                                                                                     | Proposed owner/date     |
|--------------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------|-------------------------|
| Fine-grained object ACL queries                        | A01      | Current safe fix is administrator-only; implementing user/unit ACL joins requires a shared authorization design. | File/Auth maintainers   |
| JWT revocation and refresh-token rotation              | A07      | Shared auth currently issues stateless JWTs and the browser retains a local-storage copy.                        | Auth maintainers        |
| Dependency/SBOM and immutable CI reference audit       | A03/A08  | Existing scanner/tooling was not present; adding it was outside this task's authorization.                       | Platform/CI maintainers |
| Full security-event alerting and centralized retention | A09      | Logging sinks and alert thresholds are deployment-owned.                                                         | Operations              |
| PostgreSQL certificate verification and Admin mTLS     | A04      | Requires certificates and service-network provisioning per environment.                                          | Operations              |

## Verification

- `cd vempain-file-backend && ./gradlew clean test`
- `cd vempain-auth && ./gradlew :core:test`
- `cd vempain-file-frontend && yarn lint && yarn test --runInBand && yarn build`

No existing tests were deleted or weakened. New regression coverage covers guarded GPS exclusion and scan-path
containment; existing controller, service, and token-provider tests cover the affected error and authentication paths.

## References

- Root `OWASP-2025-Top10.md`
- <https://owasp.org/Top10/2025/>
- <https://github.com/OWASP/ASVS>
- <https://owasp.org/www-project-web-security-testing-guide/>
