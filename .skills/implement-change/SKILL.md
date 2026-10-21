---
name: "implement-change"
description: "The default workflow for any code change request in this repo — a bug (\"X is broken\", an error, a GitHub issue), a feature (\"add X\", \"support Y\"), an improvement (\"make X faster / clearer\", a refactor) or anything else (docs, build, CI, tests). Takes it from request to a committed change ready for a PR: classify, design, tests first, implement, run tests and review, commit, then ask whether to open the PR. Invoke whenever the user asks for a change to the code, with or without mentioning a PR, and to address review feedback on an existing PR branch."
---

# Implement Change

Any request to change the code runs through this workflow, whether or not the
user says "PR": a one-line bug report, "add support for X", "this is slow",
"tidy up Y". One change, one branch, one commit, ready for one PR. Each step
produces something the next step reads; small changes make each step short,
not optional.

The sibling skills hold the details: **bug-fixer** (reproduce and
root-cause), **write-test-case** (test patterns), **review-changes** (the
review), **git-commit** (commit message and PR title), **compat-fixer**
(platform API questions).

Not for questions, explanations or reviews with no change requested — answer
those directly, or use **review-changes**.

## 1. Identify the change

Read the request in full — for an issue, the form fields and the whole thread;
for a plain chat request, the user's own words and any example they gave.
Then state, in two or three lines to the user:

- **Type** — this decides the commit type and the design's shape:

  | Type | Signal | Commit type |
  | :--- | :--- | :--- |
  | Bug | something worked, or should work, and doesn't | `fix` |
  | Feature | a user can do something new | `feat` |
  | Improvement | an existing feature gets better, faster or clearer | `enhance` / `perf` / `refactor` |
  | Other | docs, build, CI, tooling, tests only | `docs` / `build` / `chore` / `test` |

- **Size**:
  - **S** — one area, obvious approach, a few files;
  - **M** — several files or a real design choice;
  - **L** — crosses buckets (`channel` / `format` / `framework` / `core`),
    changes a public rule key or setting, or touches threading boundaries.
    Propose splitting an L into independently mergeable PRs before starting.
- **Scope** — what this PR will and will not do. Problems you notice along the
  way that the request doesn't need go into the final summary as follow-ups,
  not into this PR.

Ask the user only when the request is ambiguous in a way the code and the
issue can't settle (which behavior is "right" for users). Otherwise decide and
note the decision in the design.

**Review feedback on an existing PR:** check out that PR's branch
(**review-changes** step 1 for the local-checkout rules, and confirm the PR is
still open), treat each comment as a small change, continue at step 4, and
fold the result into the PR's existing commit (step 7).

## 2. Design the solution

Write the design in the session (and later in the PR description) before
editing code. Keep it proportional — a few lines for S, a short section per
heading for M and L:

- **Bug:** reproduce first, then the root cause in one sentence naming the
  exact condition (follow **bug-fixer** steps 1–3). No root cause, no fix.
- **Feature / improvement:** what the user will see and how they reach it
  (action, setting, rule key), and where the code goes per the AGENTS.md
  Package Layout rule.
- **Approach:** the change, and for M/L the alternative you rejected and why.
- **Impact:** every path the change touches (export, call, scan, dashboard,
  rule engine, settings), and what is deliberately left unchanged. Note the
  threading tier of any new code that touches PSI, and any new platform API.
- **Test plan:** the scenarios that prove it — inputs and expected results.
- **Decisions:** `Decision: X, because …` for each choice the request left open.

For size L, or any change to a boundary class or a public rule key, have a
fresh agent (no shared context) review the design against the code before you
implement, and fold in what it finds.

## 3. Branch

```bash
git fetch origin
git checkout -b <type>/<issue>-<short-slug> origin/master   # e.g. fix/1475-selection-gap
```

Without an issue, drop the number. If you are already on a branch made for
this work, stay on it. Never work directly on `master`.

## 4. Add test cases

Write the tests from the test plan **before** the implementation, following
**write-test-case**. Run them and check they fail for the expected reason —
for a bug, the reported symptom; for a feature, the missing behavior. A test
that already passes proves nothing about this change.

For an **Other** change with no runtime behavior (docs, CI, build scripts),
skip this step and say how the change is checked instead (rendered docs, a
CI run, the script's output).

When a test is impossible (pure UI layout, EDT timing), write the manual
`./gradlew runIde` steps instead and say so in the PR.

## 5. Implement

- The smallest change that does what the design says. No drive-by refactors,
  renames or formatting.
- Follow AGENTS.md: threading tiers, logging channels, package layout, and
  `plugin.xml` registration for new extensions.
- New user-visible behavior gets its docs in `docs/knowledge-base/` when an
  existing page covers that area.
- If the design turns out wrong mid-way, stop, update the design, and say what
  changed — don't drift silently.

## 6. Run tests and review

1. Run the new tests and the touched test classes until green:
   ```bash
   ./gradlew test --tests "com.itangcent.easyapi.<package>.<ClassName>Test"
   ```
2. Run the full suite once (`./script/test.sh`, in the background — it is
   slow). For new platform API calls, also run the verifier gate
   (**compat-fixer**, "Running the gate locally").
3. For a bug reproduced in `runIde`, replay exactly the recorded steps.
4. Review the branch with **review-changes** (target: this branch against
   `origin/master`). For size M and L, run that review in a fresh agent so it
   doesn't inherit your assumptions. Fix every confirmed finding and every ❌
   scope row, then re-run the affected tests.

Report failures as they are; never weaken or delete a test to get green.

## 7. Commit and ask about the PR

1. Commit with **git-commit**: user-facing subject, Problem / Root cause /
   Solution / Impact body for a fix, `Fixes #<issue>` footer. Stage only the
   files of this change. The branch holds **one commit**: every later fix,
   review change or missed file is folded into it (`git commit --amend`, or
   `--fixup` + autosquash), never added as a new commit. Multiple commits only
   for the exceptions listed in git-commit's "One commit per branch".
2. Draft the PR from `.github/pull_request_template.md`: the title equals the
   commit subject (it becomes the changelog bullet); the description carries
   the design summary, a short per-file change list, how it was tested, and
   the checklists — explain items that don't apply instead of deleting them.
   Put `Fixes #<issue>` before any other `#number`.
3. **Ask the user** whether to push and open the PR, showing the title, the
   summary and the test results. Pushing and opening a PR are visible to
   others — don't do either without a yes.
4. On yes:
   ```bash
   git push -u origin HEAD
   gh pr create --title "<subject>" --body-file <draft.md>
   ```
   Then report the PR link and the CI status once checks start.

## Final summary

End with: type and size, what changed for the user, the decisions you made,
test results (counts, anything skipped), review outcome, the commit, and any
follow-ups you deliberately left out of scope.
