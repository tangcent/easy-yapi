---
name: "compat-fixer"
description: "Handles Plugin Verifier results and IDEA compatibility issues per the project compat policy. Invoke when the CI 'Plugin Verifier' job fails on the since-build deprecation gate, when a user reports a crash or breakage on a new IDEA version, or when pluginSinceBuild is being raised."
---

# Compat Fixer

Policy owner skill for IntelliJ platform compatibility. Reactive entry point: CI
verifier failure, a P0 breakage report, or a since-build boundary bump.

## The policy (single source of truth)

- **The only gate:** the since-build IDE (currently 252, `pluginSinceBuild` in
  `gradle.properties`) must report **zero deprecated API usages** in the
  Plugin Verifier results. Enforced by the "Check since-build deprecations"
  step in `.github/workflows/ci.yml` (which also fails if the verifier matrix
  stops covering the since-build at all).
- **Deprecations reported only on newer IDEs are observation-only.** Do not fix
  them now, do not build compat layers (reflection dispatch, dual source sets,
  build-number branching) for them. They become mandatory the moment
  `pluginSinceBuild` is raised past the point where a replacement API exists.
- **When choosing a platform API while writing code**, the criterion is: not
  deprecated in the since-build SDK. Deprecation in a newer SDK alone is not a
  defect.
- **Hard failures regardless of IDE** (enforced by `verifyPlugin` itself via
  `failureLevel` in `build.gradle.kts`): compatibility problems, internal-API
  usages, override-only-API usages. These red the gradle task directly — never
  add them to `failureLevel` exclusions to "fix" a warning.

## Workflow A — CI gate failed (since-build has deprecations)

1. Read the failing job log. The gate prints the matched summary line, e.g.
   `Plugin ... against IC-252.x: Compatible. N usages of deprecated API`.
   Full detail lives in the `plugin-verification-reports` artifact, or re-run
   locally: `./gradlew verifyPlugin` (reports under
   `build/reports/pluginVerifier/`).
2. For each reported usage, find a replacement **that exists in the
   since-build SDK** and migrate to it directly — no version branching.
3. If no non-deprecated replacement exists in the since-build SDK, do not
   invent a compat layer. Evaluate raising `pluginSinceBuild` (Workflow C) or,
   as a last resort, an issue note explaining the accepted exception.
4. Verify locally that the gate would pass, then commit with the
   `fix(compat): <subject>` conventional-commit format.

## Workflow B — breakage reported on a newer IDEA (P0/P1)

Classify first: crash / `NoSuchMethodError` / feature dead = P0; silently wrong
behavior = P1. Both block release on **any** matrix IDE. Fix priority ladder:

1. **Fix directly** (adapt to the new API/behavior at the breakage site).
2. **Raise `pluginSinceBuild`** (Workflow C) if the fix is materially cheaper
   on the newer baseline.
3. **Temporary `until-build` cap** only if neither works — record a removal
   condition in the issue.

Never pre-emptively add dispatch layers for *deprecation* (see policy); this
ladder is for actual removal/breakage only.

## Workflow C — raising pluginSinceBuild

Editing `gradle.properties` `pluginSinceBuild` is a compat event, not just a
version bump:

1. The Verifier matrix is `ides { recommended() }` in `build.gradle.kts`. It
   usually includes the since-build, but that is a JetBrains-side data set, not
   a guarantee: after the bump, the CI gate asserts that a
   `Plugin ... against <PRODUCT>-<newSince>.<build>: ...` summary line exists
   and fails if none does. If the gate reports that, add the missing IDE
   explicitly to the `ides { ... }` block in `build.gradle.kts` rather than
   assuming `recommended()` covers it.
2. Run `./gradlew verifyPlugin`. **Every deprecation now on the new since-build
   line is a required fix in this same PR** — prior "observation-only" warnings
   are settled here.
3. Remove any compat workarounds whose minimum supported version they covered
   (grep for `compat` commits / build-number guards).
4. Keep `script/package.sh` legacy best-effort builds for users below the new
   boundary; the mainline no longer verifies those ranges.

## Anti-patterns (forbidden)

- Reflection / `ApplicationInfo.build` dispatch / dual source sets introduced to
  silence a newer-IDE deprecation warning.
- "Fixing" a newer-IDE-only deprecation by switching to an API absent from the
  since-build SDK (turns a warning into a crash for the oldest users).
- Treating the GitHub `::warning::` from "Surface deprecated API usages as
  warnings" as a to-do item.
