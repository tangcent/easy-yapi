---
name: "bug-fixer"
description: "Systematic, test-first bug fixing for the EasyYapi plugin: reproduce, find the root cause, write a failing regression test, make the minimal fix, verify. Invoke when the user reports an error, a failing test, a GitHub issue, an IDE freeze or exception, wrong export output, or other unexpected behavior that needs debugging and fixing."
---

# Bug Fixer

Fix the mechanism that produced the bug, prove it with a test that failed
before the fix, and change nothing else.

A bug-fix request normally runs through **implement-change**, which uses this
skill for the reproduce, root-cause and verify steps.

## 1. Understand the report

- **GitHub issue:** it comes from the bug form (`.github/ISSUE_TEMPLATE/bug_report.yml`).
  Read these fields first — they name the entry point before you open code:
  the action (export / call / scan / freeze), the framework(s), the custom-rule
  setup (`.easyapi/*.rules`, legacy `.easy.api.config`, `~/.easyapi/*.rules`,
  remote rules), the minimal repro code, and the logs.
- **Stack trace / `idea.log`:** find the first frame in
  `com.itangcent.easyapi`. Note the thread name — `AWT-EventQueue` (EDT),
  `EasyAPI-background`, or a coroutine worker — threading bugs are common here.
- **Failing test:** read the assertion and the test's setup (loaded files,
  config reader, settings) before the production code.
- **Too little information?** Ask the reporter for the missing form field
  (usually repro code or logs at DEBUG/TRACE) instead of guessing at a fix.

Then decide: is this actually a bug, or expected behavior / a rule-config
issue? If the behavior is configurable by a rule key, check the rule guide
(`docs/knowledge-base/rule-guide.md`) before changing code.

## 2. Reproduce

Reproduce in a test whenever the code path is reachable from one — that is
both the proof and the regression guard:

1. Turn the reporter's repro code into fixture sources under
   `src/test/resources/` (the light fixture needs **explicit imports even for
   same-package classes**, and annotation stubs loaded first).
2. Drive the same entry point the user used (the exporter, the formatter,
   `ApiScanner`, the action's service) — not a lower-level helper that happens
   to be easier to call.
3. Use the reporter's own example, not a simplified one you made up; the
   simplified one often misses the trigger.

Follow the **write-test-case** skill (`.skills/write-test-case/SKILL.md`) for
the pattern and utilities. If the bug only shows in a real IDE (EDT freeze, UI
state, plugin loading), reproduce with `./gradlew runIde` and write down the
exact steps — they become the verification later.

## 3. Find the root cause

Trace from the symptom to the first place the code does something wrong, and
be able to state it in one sentence (`` `scanClasses` drops classes whose
recognizer matches nothing, so interfaces are never expanded ``). Where this
codebase usually breaks:

| Symptom | Usual cause | Where to look |
| :--- | :--- | :--- |
| `Read access is allowed from inside read-action only`, freeze, `slow operations on EDT` | PSI touched without a read action, or work launched with an inherited EDT context | AGENTS.md → Threading Model; boundary classes (`ScriptXxxContext`, EP implementations) must self-protect every public member, including `toString`/`hashCode`/`equals` |
| Crash only with custom Groovy rules | Rule script called an unprotected member of a bound context object (the #1432 class of bug) | `core/rule/`, `Jsr223ScriptParser`, the script context classes |
| Field / param missing or typed as a plain value | Unresolved type (missing import in fixture sources, or a generic not bound) | `core/psi/` type resolution, `ObjectModel` building |
| Rule seems ignored | Wrong rule key, rule source not loaded, or cached rules | `core/config/`, `RuleProvider`, the console with log level lowered |
| Works on one framework, not another | Logic in one `framework/<id>/` exporter that is missing in its sibling | Compare with the sibling exporter |
| Wrong only on Windows CI | Golden file read without CRLF normalization | Use `ResultLoader` / `ResourceLoader`, never `File.readText()` |
| `NoSuchMethodError` / broken on a new IDE version | Platform API change | Use the **compat-fixer** skill instead |

Check whether the same mistake exists at sibling call sites or in sibling
implementations (other channels/frameworks). Fix those too if the same
mechanism is at fault; mention anything larger as a follow-up instead of
widening the change.

## 4. Write the failing test first

- Add the regression test to the existing `{ClassName}Test.kt` if there is one.
- Run it and **confirm it fails for the reported reason** (the assertion, not a
  setup error). A test that passes before the fix does not cover the bug.
- Name it after the symptom, not the issue number.

```bash
./gradlew test --tests "com.itangcent.easyapi.{package}.{ClassName}Test"
```

When a test is genuinely impossible (pure UI/EDT timing), say so explicitly
and rely on the written `runIde` steps.

## 5. Fix

- Minimal change at the root cause; no drive-by refactors, renames or
  formatting.
- Follow AGENTS.md: threading tiers (`read {}` / `readSync {}` / `swing {}`),
  logging channels (one channel per event, no `LOG.error` / `LOG.debug`, no
  swallowed exceptions), and the package-layout rules.
- Add a short comment only where the fix would look wrong or redundant to the
  next reader (e.g. why a read action is needed on a background path).

## 6. Verify

1. The new test passes.
2. The touched test classes and their neighbors pass; run the full suite
   (`./gradlew test` or `./script/test.sh`) before committing.
3. If you reproduced in `runIde`, replay **exactly** the recorded steps and
   confirm the symptom is gone.
4. If anything fails, go back to step 3 — don't patch the test to match.

## 7. Commit

Use the **git-commit** skill: a `fix(<scope>):` subject that names the
user-visible symptom, and a body answering Problem / Root cause / Solution /
Impact, with `Fixes #<issue>` when there is one.

## Example

```
Issue: "Export a method → 'No API endpoints found.' when @GetMapping is on
the interface and @RestController on the implementation."

1. Form: action = export, framework = Spring MVC, repro = interface + impl.
2. Fixture: copy interface + impl into src/test/resources/, export the
   interface method through the same selection path the action uses → empty.
3. Root cause: scanSelection hands the interface to scanClasses, which drops
   classes with no framework annotation; implementations are never examined.
4. Test: testInterfaceMethodExportsImplementationEndpoint — fails (0 endpoints).
5. Fix: expand selected interfaces/abstract classes to their inheritors when
   the explicit selection yields nothing; PSI reads wrapped in read {}.
6. Test passes; export tests pass; runIde replay of the reporter's steps OK.
7. fix(export): support exporting a contract method … Fixes #1475
```
