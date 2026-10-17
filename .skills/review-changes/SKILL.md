---
name: "review-changes"
description: "Reviews any code change in this repo — a pull request, a commit, a commit range, a branch, the uncommitted working tree, or named files/packages — from a pinned local checkout. Produces one report: a per-file change list with a scope check, the project-rule check (threading, logging, package layout, compat, commit/PR format), correctness findings, and the test gaps with concrete scenarios. Invoke when the user asks to review a PR, commit, branch, diff or piece of code, asks what changed or whether a change is safe, or asks what tests a change needs / for a test plan."
---

# Review Changes

One review procedure for every kind of target. All layers read the **same
local snapshot**, so the change list, the rule check, the bug findings and the
test plan describe the same code.

If your environment provides its own PR-review skill (a user-level or global
one), run that one for the report shape and use this skill as the
project-specific rule source for its checks.

## 1. Resolve the target to a local snapshot

Record, before reading any code: the checkout path, the **base** and **head**
revisions, and (for PRs and branches) the merge-base. Keep using them for the
whole review.

| Target | Base / head | Change list |
| :--- | :--- | :--- |
| PR (`#123`, URL) | pinned from `gh pr view` (below) | `git diff --name-status <merge-base> <head>` |
| Commit `<sha>` | `<sha>^` / `<sha>` | `git show --name-status --format= <sha>` |
| Range `a..b` | `a` / `b` | `git diff --name-status a b` |
| Branch | `origin/master` / branch tip | `git diff --name-status origin/master...<branch>` |
| Uncommitted work | `HEAD` / working tree | `git status --short`, `git diff HEAD` (include untracked files) |
| Files / package ("review `core/export/`") | — / current `HEAD` | read the code as it is; use `git log -p -- <path>` for recent history |
| Nothing specified | the current branch vs `origin/master`; if there is no branch work, the uncommitted diff | as above |

### Pull requests: always review from a local checkout

Use `gh` only for PR **metadata, status and CI** — never `gh pr diff`, the
per-file patch API, or repeated remote diff reads to review code.

1. Pin the revisions:
   ```bash
   gh pr view <n> --json title,body,url,baseRefName,baseRefOid,headRefName,headRefOid,isCrossRepository,headRepositoryOwner
   ```
2. Pick a checkout, in this order:
   1. **The current checkout**, if `git rev-parse HEAD` equals the pinned
      `headRefOid` (a matching branch name alone is not enough).
   2. **An existing worktree** at that head (`git worktree list --porcelain`).
   3. **A new review worktree** otherwise:
      ```bash
      git fetch origin pull/<n>/head          # works for fork PRs too
      git rev-parse FETCH_HEAD                 # must equal headRefOid
      git worktree add --detach ../easy-yapi-review-<n> <headRefOid>
      ```
3. Make sure the base commit is local too, then compute
   `git merge-base <baseRefOid> <headRefOid>` once.
4. Never reset, clean, stash or switch a checkout that has someone's
   uncommitted work to make it fit — use a separate worktree instead. Never
   push to the PR branch as part of preparing a review.
5. If fetching or creating the worktree fails, report the blocker; do not fall
   back to reviewing a remote diff.
6. If the PR head moves before you report, say which head the review covers.

Remove a worktree you created when the review is done
(`git worktree remove ../easy-yapi-review-<n>`), unless the user wants to keep
working in it.

## 2. Establish the purpose

From, in order: the linked issue (`Fixes #…` — read its bug-form fields), the
PR title and body, the commit messages. State it in one or two sentences,
numbered if there are several goals. If nothing states it, infer it from the
diff and **label it as inferred** — the scope check is meaningless without it.

## 3. Change list and scope check

Read every file's diff before writing its row; harmless-looking file names are
where unrelated changes hide.

| File | What changed (effect, in plain words) | Goal served | Verdict |
|---|---|---|---|

- **What changed** describes behavior ("exporting an interface method now
  finds the implementation's endpoint"), not code ("modified `scanSelection`").
- **Verdict:**
  - ✅ serves a goal, proportionate approach;
  - ⚠️ serves a goal but questionable — far bigger than needed, deletes code
    that still has callers, changes user-visible behavior the description
    doesn't mention, or is half done;
  - ❌ serves no goal (drive-by refactor, unrelated docs/config edits) — say
    where it belongs instead.
- Split a file into several rows when its hunks serve different purposes.
  Collapse golden files, generated files and mechanical renames into one row
  unless something in them doesn't follow from the rest of the diff.
- Put each test file right after the file it tests.
- For a files/package target with no diff, replace this table with a short
  map of the code (each class's role) and skip the scope verdicts.

## 4. Project-rule check

Check the change against AGENTS.md. Report only rules the change actually
touches, each with `file:line`:

- **Threading** — PSI/VFS reads inside `read {}` / `readSync {}` or a
  documented `@requires ReadAction` with internal callers only; boundary
  classes (`ScriptXxxContext`, EP implementations) self-protect every public
  member including `toString`/`hashCode`/`equals` and `by lazy`; writes/UI via
  `write {}` / `swing {}`; no `launch(Dispatchers.Default)` from EDT or startup
  contexts.
- **Logging** — one channel per event (`NotificationUtils` / `IdeaConsole` /
  `IdeaLog`); no `LOG.error`, `LOG.debug`, `LOG.trace`, `println`,
  `printStackTrace`; throwable passed last; no silent `runCatching{}.getOrNull()`
  or empty `catch`.
- **Package layout** — new code in the right bucket (`channel/` / `format/` /
  `framework/` / `core/`); no `core.*` import of a concrete
  `channel.<id>` / `format.<id>` / `framework.<id>` package; new extensions
  registered in `plugin.xml`.
- **Platform compat** — new platform API calls not deprecated, internal or
  override-only in the since-build SDK (see the **compat-fixer** skill).
- **Tests** — golden files read via `ResultLoader` / `ResourceLoader`, never
  `File.readText()`; fixture tests use the `test` prefix.
- **Commit / PR format** — the branch is one commit (more only for the
  git-commit exceptions); every commit subject passes the conventional
  format with an allowed type (no `fixup!`, merge commits or `ci:`), and the PR
  title is user-facing, since it becomes the changelog bullet (see the
  **git-commit** skill).
- **PR template** — Architecture & Threading and Logging checklist items that
  don't apply are explained, not deleted.

## 5. Correctness review

Look for real bugs in the changed code and the code it calls or is called
from: wrong conditions, null/empty inputs, unhandled framework variants
(Spring vs JAX-RS vs Feign vs gRPC), rule keys that change meaning, caching
that serves stale results, cancellation and threading races, behavior that
differs between the scan, export and dashboard paths. Use a built-in code-review
tool if one is available, pointed at the **same local checkout and revisions**;
otherwise review the local diff yourself and say so.

Each finding: `file:line`, the concrete input or state that triggers it, and
what goes wrong. Leave out style opinions and anything you could not tie to
a trigger.

## 6. Test gaps

For each changed production class (`src/main/**`), find its tests before
judging coverage:

```bash
find src/test -name "<ClassName>Test.kt"
grep -rl "<ClassName>" src/test/kotlin | head
```

Count it as covered only if a test exercises the **changed** lines. For each
gap give the priority, the pattern from the **write-test-case** skill (A simple
unit · B IDE fixture · C `ResultLoader` golden · D action · E parity), and
concrete scenarios — an input and its expected observable result:

- **HIGH** — export output, PSI/type resolution, rule evaluation, threading
  boundaries, user-visible settings behavior;
- **MEDIUM** — services, utilities, data transformations;
- **LOW** — constants, simple data classes, pure UI layout.

A behavior change with no test also gets a ⚠️ on its change-list row. Changed
golden files under `src/test/resources/result/` are behavior changes — check
each diff is intended. A refactor needs evidence that behavior is unchanged
(existing tests still pass, or a parity test), not one new test per moved
class.

When the user asked only for a test plan, run steps 1, 2 and 6, and write the
full plan to `.temp/test-plan-<slug>.md` (`.temp/` is git-ignored). Don't
commit plans or put them in `docs/` unless asked — they go stale.

## 7. Report

One message, in this order, skipping empty sections:

1. **Target** — what was reviewed: checkout path, base/head (and merge-base).
2. **Purpose** — marked *inferred* if it was.
3. **Change list** — the table.
4. **Scope** — one line ("all changes serve goals 1–2"), or the ⚠️/❌ rows
   with a concrete suggestion each (split out, shrink, add a test, mention it
   in the PR body).
5. **Project rules** — violations with `file:line`.
6. **Bugs** — most severe first; "none found" if none.
7. **Test gaps** — HIGH items with scenarios; the rest summarized.

## Acting on the result

- **Your own branch / PR:** fix only what the user asks for, after checking the
  PR is still open, and fold each fix into the PR's single commit (git-commit,
  "One commit per branch"); update the branch with `git push --force-with-lease`,
  only on your own PR branch.
- **Someone else's PR:** report only. Posting review comments, approving, or
  pushing to their branch needs the user's explicit go-ahead.
- To write the missing tests, follow **write-test-case**; to fix a confirmed
  bug, follow **bug-fixer**.
