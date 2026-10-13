---
name: file-acl-rules
description: Apply the file backend's resource-based ACL authorization correctly (readableFiles, requireModify, fail closed, no ACL on tags or metadata). Use whenever a change reads, lists or mutates files.
---

# ACL rules of vempain-file-backend

- Authorization is resource based, never role based: no `hasRole`, `ROLE_*`, `@PreAuthorize("hasRole(...)")` or test-mode bypass.
- Only `FileEntity` subclasses carry an `acl_id`. Tags, metadata rows, file groups, locations, guards, export files, queues and task
  records have no ACL and must not be ACL checked; checks on tag or metadata operations apply to the files they rewrite.
- `FileAclService` is the single entry point:
    - every paged file listing wraps its specification in `fileAclService.readableFiles(spec)`;
    - single-file endpoints call `requireRead/requireModify/requireDelete(file)`;
    - bulk operations call `requireModifyBulk(files)` once, synchronously, before submitting a background task (fail closed, 403 lists the
      denied ids).
- Fail closed: a non-positive or orphaned `acl_id` is denied for everyone; `FileAclRepairSchedule` repairs such rows daily.
- System-wide operations (scanning, data set generation, statistics, refresh schedules, admin-side republish) are intentionally
  unfiltered; do not add per-file checks there.
- Tests: every ACL dependent path needs a granted and a denied case. In `*CTC` use `seedFileRow` (ACL for user 1, the request principal)
  and `seedFileRowWithAcl(id, ..., 2L, ...)` or `(..., 1L, true, false, false)` for denied cases. Keep `FileAclService` above 95% line
  coverage.
- Record authorization changes in `security/OWASP-2025-audit-report.md`.
