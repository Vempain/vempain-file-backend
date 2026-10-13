---
name: run-backend-tests
description: Build and test the file backend locally (Gradle, Testcontainers, exiftool, GitHub Packages credentials, offline builds against a locally published library). Use before reporting any change as done.
---

# Run the vempain-file-backend tests

- Requirements: Java 25 toolchain (Gradle resolves it), a running Docker daemon (Testcontainers PostgreSQL for `*CTC`/`*ITC`),
  `exiftool` on the host, and GitHub Packages credentials (`gpr.user`/`gpr.token` in `~/.gradle/gradle.properties` or
  `GITHUB_ACTOR`/`GITHUB_TOKEN`) for `vempain-auth-*`, `vempain-common-*` and `vempain-admin-backend-api`.
- Commands:
    - compile only: `./gradlew :service:compileJava :service:compileTestJava`
    - one class or pattern: `./gradlew :service:test --tests '*MetadataControllerCTC'`
    - everything: `./gradlew clean test` (takes a few minutes; run one repository's suite at a time, parallel suites exhaust memory)
- Results: `service/build/test-results/test/*.xml` and `service/build/reports/tests/test/index.html`. A 500 in a CTC is explained in the
  XML `system-out` (`GlobalExceptionHandler` logs the exception with a correlation id).
- Unpublished shared library: publish it to the local Maven repository from its repo (`./gradlew -PreleaseVersion=<version> publishToMavenLocal -x test`), bump
  `gradle/libs.versions.toml` here, and run Gradle with an
  init script that adds `mavenLocal()` first to `allprojects.repositories` (`./gradlew -I local-maven.init.gradle ...`); the checked-in
  build files list only GitHub Packages.
- Test naming is fixed: `UTC` unit (Mockito), `CTC` controller (`AbstractControllerCTC`, MockMvc), `ITC` integration (Spring Boot +
  Testcontainers), `JTC` JSON contract. Helpers carry no suffix.
- Report the numbers (tests run, failures) in the response; never claim a suite passed without the output.
