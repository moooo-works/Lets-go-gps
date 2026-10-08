# Android 17 developer settings review

Reviewer: gpt-6-astra / medium — independent native worker /root/android17_review

Reviewed: 2026-10-08 17:30:27 +0800 (Asia/Taipei)

Source: `1ecc8a4c4d9e675971778fa87e580fd96b9da18f`; base: `012ec15`; five files, 100 insertions and one deletion. This is the new Android 17 fix review, separate from the floating companion review.

**Summary**

- PASS: the Android 17 fix removes the false “developer options disabled” result while preserving Android's actual mock-location permission check. No blocking finding.

- The saved real-device test and health-screen image support the reported fix. This verdict covers the Android 17 bug; floating companion acceptance remains separate.

- The coordinator can carry this verified fix into the floating branch and resume its device tests. PR synchronization and merge gates remain the coordinator's responsibility.

## Outcome — PASS

- The current Ask explicitly prioritizes this bug before floating companion testing. Both affected consumers now avoid interpreting the hidden setting as disabled on API 37 and later. Health checks reuse `NotApplicable`; the existing screen omits that item and still checks mock permission independently.

- INV-A17-1: the official [Settings.Global reference](https://developer.android.com/reference/android/provider/Settings.Global#DEVELOPMENT_SETTINGS_ENABLED) says third-party apps receive zero for this setting. The coordinator recorded Pixel 7 Pro API 37, build CP3A.260905.009, shell value 1 but app value 0 and mock AppOps mode 0. Independently inspected saved instrumentation logs show the old health result failing and the fixed version passing its one test. The inspected `android17-health-fixed.png` shows “全部通過 — 可開始模擬” with the mock-app item passed and no false disabled item.

- INV-A17-2: the engine still checks `OPSTR_MOCK_LOCATION` using the process UID and app package. Only `MODE_ALLOWED` produces `Allowed`; every other mode produces `NotAllowed`; exceptions produce `CheckFailed`. Health mock checks still fail for denial, missing AppOps service, or exceptions. `MapViewModel.ensurePermission()` accepts only `Allowed`, so hiding an unreadable developer setting does not grant permission or suppress the actual gate.

- INV-A17-3: below API 37, both branches execute their original developer-setting checks. Provider setup, location injection, errors, settings navigation, permissions, lifecycle, schema, and ads are unchanged. No new privilege or exported interface is introduced.

## Minimality — PASS

- Deleting either production change leaves one affected user path broken: health checks would still block, or the engine would still reject permission. Deleting all developer checks would unnecessarily change older versions. Treating zero as enabled would make a false status claim.

- Reuse is already appropriate: existing `NotApplicable`, AppOps checks, status types, and UI filtering handle the fix. Extracting a shared helper for two short version guards would add indirection without reducing the substantive logic. The three test files cover the two consumers and the real application process; no refactor or dependency is needed.

## Conformance — PASS

- Inspected the full frozen diff and surrounding consumers. The worktree HEAD matched the reviewed commit and its source status was clean. Scope agrees with the captured Ask, design, and five-file review facts.

- Reused completed coordinator validation; did not rerun broad tests. Saved Gradle log ends in `BUILD SUCCESSFUL` for test, lintDebug, and assembleDebug. Independently counted XML results: 253 tests, zero failures, zero errors, 66 skipped. Health tests: 9 executed, zero failures/skips. New engine tests: 3 executed, zero failures/skips, covering allowed, denied, and exception outcomes.

- Instrumentation logs show one failure before the fix and `OK (1 test)` after it. Those runs validate the reported device behavior; they do not establish successful location injection or complete floating companion acceptance. The coordinator's earlier focused red counts were health 9/2 failures and engine 3/3 failures.

- Lint command completion is not a clean lint report: saved XML contains 34 Errors, 91 Warnings, and 3 Hints. The coordinator identifies the errors as existing; this five-file diff does not change lint configuration or introduce a new permission bypass. No adjacent cleanup is required by this review.

- Nonblocking limit: the official reference does not identify the rollout boundary or Android 16 backports. API 37 is supported by the reproduced device and requested fix. Do not claim that API 36 can never hide this value; broader compatibility work should follow a reproduced older-device failure or a separate owner request.

## Verdict

Verdict: PASS

Outcome: PASS

Minimality: PASS

Conformance: PASS

Blocking findings: none for `1ecc8a4c4d9e675971778fa87e580fd96b9da18f`.

Review limits: shared filesystem with instruction-enforced read-only source access, not OS isolation; same provider family. Only this report was written. No source, Git, configuration, notebook, device, or network state was changed. Required notebook, STATUS, route, commit, and PR records remain with the coordinator.

Self-check: Compared the complete five-file diff with the authorized outcome; traced both permission consumers and the caller's rejection branches; checked allowed/denied/error evidence, older-version branches, real-device logs and screenshot, and test/lint totals; attempted deletion, merge, and reuse simplifications; kept Android 16 uncertainty and floating acceptance outside this scoped PASS.
