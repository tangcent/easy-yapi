---
name: "bug-fixer"
description: "Identifies and fixes bugs systematically. Invoke when user reports an error, test failure, or unexpected behavior that needs debugging and fixing."
---

# Bug Fixer

This skill provides a systematic approach to identify and fix bugs.

## Workflow

### 1. Analyze the Error

- Read the error message carefully
- Identify the affected code files and functions
- Determine if this is truly a bug or expected behavior

### 2. Verify the Bug

Before fixing, verify:

- Is the error reproducible?
- Is there a test case that demonstrates the bug?
- Is the error in the code or in the test/usage?

### 3. Find Root Cause

- Trace the error back to its source
- Use logging or debugging to understand the flow
- Identify the exact line/condition causing the issue

### 4. Write/Update Test Case

**CRITICAL: Always write or update test cases BEFORE fixing the bug.**

- Create a test that reproduces the bug (should fail)
- This ensures we understand the bug correctly
- Provides regression protection

### 5. Fix the Bug

- Make minimal changes to fix the issue
- Don't refactor or make unrelated changes
- Ensure the fix addresses the root cause

### 6. Verify the Fix

- Run the test case - it should now pass
- Run related tests to ensure no regression
- If tests fail, re-analyze and fix again

## Example Workflow

```
User reports: "NullPointerException in UserService.getUser()"

1. Read UserService.getUser() and trace the null path
2. Check if null input is valid or should be rejected
3. Write test: testGetUserWithNullInputThrowsException()
4. Run test - confirm it fails
5. Add null check and throw IllegalArgumentException
6. Run test - confirm it passes
7. Run all UserService tests - confirm no regression
```

## Important Guidelines

- **Never skip the test case step** - tests document the bug and prevent regression
- **Fix the root cause, not just the symptom** - a quick fix often leads to more bugs
- **Keep changes minimal** - don't refactor while fixing
- **Document the fix** - add comments explaining non-obvious fixes
