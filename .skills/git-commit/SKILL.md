---
name: "git-commit"
description: "Writes commit messages and PR titles in this repo's conventional-commit format, with a user-facing subject that doubles as the changelog entry. Invoke when the user asks to write a commit message, create a commit, title a PR, squash/reword commits, fold follow-up changes into an existing commit, or format a git commit."
---

# Git Commit

A commit here has two readers:

- **Plugin users** read the **subject**. PRs are squash-merged, so the PR title
  becomes the subject on `master`; `script/release.sh` copies subjects (never
  bodies) into `CHANGELOG.md`, and `.github/workflows/release.yml` copies the
  `feat`/`fix`/`refactor`/`perf`/`docs` subjects into the GitHub release notes.
- **Developers** read the **body**: problem, root cause, solution, impact.

So the subject says *what the user gets*; the body says *how*.

## Workflow

1. **Read the change.**
   ```bash
   git diff --cached --stat && git diff --cached   # staged changes
   git log --oneline -15                            # house style
   ```
   If nothing is staged, ask which files belong to the commit rather than
   running `git add -A` (the tree often holds unrelated local edits).
2. **Pick the type and scope** (tables below).
3. **Write the subject** (subject rules below).
4. **Write the body.** Required for `fix`, `feat`, `refactor` and `perf`. A
   one-line `docs`/`chore`/`test` commit may skip it.
5. **Add the issue footer** if an issue exists.
6. **Commit with a HEREDOC** so the formatting survives:
   ```bash
   git commit -m "$(cat <<'EOF'
   fix(export): keep record field comments in exported docs

   <body>

   Fixes #1473
   EOF
   )"
   ```

## Types

`.github/workflows/auto-label.yml` checks the first line of **every commit in
a PR** against `^(type)(\(scope\))?!?: .+` (or `release vX.Y.Z`) and comments
on the PR if any commit fails. Only these types pass:

| Type | Use for | PR label | Changelog section |
| :--- | :--- | :--- | :--- |
| `feat` | Something new a user can do | `type: new feature` | Added |
| `fix` | Something that was broken for a user | `type: bug` | Fixed |
| `refactor` | Restructuring with no behavior change, or a user-visible rename/reorg | `type: enhancement` | Changed |
| `enhance` | Improvement to an existing feature | `type: enhancement` | Improved |
| `perf` | Faster or cheaper, same behavior | `type: enhancement` | Improved |
| `docs` | Documentation only | `type: doc` | Improved |
| `test` | Tests only | `type: test` | Improved |
| `build` | Gradle, dependencies, packaging | `type: chore` | Improved |
| `chore` | Tooling, CI, scripts, repo maintenance | `type: chore` | Improved |
| `style` | Formatting only | `type: chore` | Improved |
| `amend` | Small correction to a recent change | `type: amend` | Improved |
| `release` | `release vX.Y.Z` only (made by the release flow) | `release` | — |

- **There is no `ci` type** — CI changes are `chore(ci): …`.
- `type!:` marks a breaking change.
- Because every PR commit is checked, `fixup!`, `squash!` and `Merge branch …`
  commits fail the check. Squash them (`git rebase -i --autosquash`) before
  pushing a PR branch.

**Scopes** are the area a user would recognize, usually a top-level package or
feature: `export`, `yapi`, `postman`, `markdown`, `channel`, `settings`,
`dashboard`, `search`, `editor`, `scan`, `rules`, `ai`, `psi`, `compat`, `ci`,
`scripts`. Check `git log --oneline` for the scope already used for that area.

## Subject rules

- **Format:** `<type>(<scope>): <subject>`; scope is optional.
- **Lowercase, imperative, no trailing period:** `fix(yapi): surface the
  server's rejection reason`. Not `Fixed …`, not `Fixes …`.
- **Length:** aim for ≤ 50 characters; never over 72. GitHub appends
  ` (#1234)` on squash-merge, so leave room for it.
- **User-facing, always.** Describe what the user observes or can now do, not
  the mechanism. Internal words (registry, lifecycle, extension point,
  generation-scoped, read action, refactor of X into Y) go in the body.
- **`fix`:** name the symptom that is now gone, in the user's terms.
- **`feat`:** name the capability.
- The changelog bullet keeps the `type(scope):` prefix, so the whole string
  must read as a release-note line.

| Avoid (internal) | Prefer (user-facing) |
| :--- | :--- |
| `fix(export): expand interfaces via ClassInheritorsSearch` | `fix(export): support exporting a contract method and its docs` |
| `fix(yapi): parse errmsg from save response` | `fix(yapi): surface the server's rejection reason` |
| `feat(settings): introduce project feature registry` | `feat(settings): allow turning off most features` |
| `fix(dashboard): merge snapshot maps` | `fix(dashboard): merge model body with cached edits so new DTO fields appear` |
| `fix: bug` / `update code` / `WIP` | anything specific |

No emojis, no passive voice, no vague subjects.

## Body rules

- One blank line after the subject; wrap at 72 columns.
- **`fix`** — answer four questions, in this order, as short paragraphs:
  1. **Problem** — what the user saw, and on which entry point (export,
     call, scan, a specific action).
  2. **Root cause** — the exact condition in the code, naming the class or
     method (`` `scanSelection` drops … ``).
  3. **Solution** — what changed and why that fixes the root cause, including
     any threading or logging consequence.
  4. **Impact** — what else this path touches and what is deliberately left
     unchanged.
- **`feat` / `enhance`** — what the user can now do and how to reach it, then
  the notable implementation choices and their limits.
- **`refactor` / `perf`** — why, and evidence that behavior is unchanged (tests
  run, measurements).
- When one squashed PR contains several logical changes, keep one paragraph
  group per change, each led by its own `* type(scope): …` line — this is what
  GitHub's squash message produces and what the history already looks like.

## Issue footer

At the very bottom, after a blank line:

- `Fixes #123` — the commit fully resolves the issue (GitHub closes it on merge).
- `Refs #123` — related or partial.

In a PR description, put the issue reference before any other `#number`:
`.github/workflows/pr-close-issue.yml` acts on the first one it finds.

## Example

```text
fix(export): support exporting a contract method

Exporting a method from the context menu failed with "No API
endpoints found." whenever the mapping annotations sit on an
interface and the framework annotation sits on its implementation.

Root cause: `scanSelection` resolves the selection to its containing
class and hands it to `scanClasses`, which drops any class whose
framework recognizer matches nothing. A plain interface carries no
`@RestController`, so it was rejected before a method was examined.

Solution: when an explicit selection yields no endpoints, expand the
selected interfaces and abstract classes through
`ClassInheritorsSearch` and scan the implementations. PSI reads added
on this path are wrapped in a read action, because it runs on
`EasyAPI-background` with no read access held.

Impact: the explicit-selection path only. A full scan and the
dashboard index are untouched.

Fixes #1475
```

## One commit per branch

A branch / PR holds **exactly one commit**. Every later change to the same
work — a review fix, a missed file, a test tweak, a reworded message — is
folded into that commit, never added as a new one:

```bash
# fold staged changes into the branch's only commit
git commit --amend --no-edit

# or, when the commit to fix is not the tip
git commit --fixup=<sha>
git rebase -i --autosquash origin/master   # non-interactive: GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash origin/master
```

- Keep the original message unless the fix changes what the commit does; then
  update the body (and the subject, if the user-visible effect changed).
- Before folding into a pushed commit, check the PR is still open — a merged
  PR needs a new branch and PR instead.
- After rewriting a pushed branch, update it with
  `git push --force-with-lease` — only on your own PR branch, never on
  `master`, `stable/*` or a branch someone else pushes to.
- To bring the branch up to date, rebase on `origin/master` rather than
  merging it in (a merge commit is a second commit and fails auto-label).

**Allowed exceptions** — several commits only when each one is deliberately
self-contained and meant to be read on its own, and the user agreed or asked:

- a large mechanical change (rename, move, reformat) kept apart from the
  behavior change it enables, so the behavior diff stays reviewable;
- a planned series of independently meaningful steps in one PR;
- `release vX.Y.Z` commits made by the release flow;
- a revert, which stays its own commit.

Each commit in such a series still follows every rule above, and the PR is
still squash-merged unless the user says otherwise.

## Rewording and PR titles

- Reword the last commit: `git commit --amend` (only if it is not pushed, or
  it is your own PR branch — then `git push --force-with-lease`).
- A PR title follows exactly the same subject rules; it is what lands on
  `master`. When a PR's purpose changes during review, update the title too.
