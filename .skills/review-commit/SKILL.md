---
name: "review-commit"
description: "Reviews a git commit to create a test plan document. Invoke when user asks to review a commit, analyze changes, or create test coverage plan for recent changes."
---

# Review Commit

This skill analyzes a git commit and generates a comprehensive test plan document for all changed files.

## When to Use

Invoke this skill when:
- User asks to "review a commit" or "analyze a commit"
- User wants to create a test plan for recent changes
- User mentions "changed files" in context of testing
- User wants to ensure test coverage for new code

## Workflow

### Step 1: Collect Changed Files

Use git commands to identify all changed files in the target commit:

```bash
# Get commit info
git log -1 --pretty=format:"%H%n%an%n%ad%n%s%n%b"

# Get all changed files with status (A=Added, M=Modified, D=Deleted)
git diff HEAD~1 --name-status

# Get list of changed files only
git diff HEAD~1 --name-only
```

### Step 2: Categorize Changes

Organize the changed files into categories:

1. **New Classes (Added - A)**: Files that were added in the commit
2. **Modified Classes (Modified - M)**: Files that were modified
3. **Deleted Classes (Deleted - D)**: Files that were removed

Filter to focus on source code files:
- `src/main/kotlin/**/*.kt` - Main source files
- `src/main/java/**/*.java` - Java source files
- Exclude test files from the "needs test" list
- Exclude config files, resources, and build files

### Step 3: Read and Analyze Each File

For each changed source file:

1. Read the file content using `mcp_Filesystem_read_text_file` or `Read` tool
2. Analyze the class structure:
   - Class name and purpose
   - Key methods and their responsibilities
   - Dependencies and integrations
   - Complexity level
3. Determine testing priority:
   - **HIGH**: Core business logic, complex algorithms, public APIs
   - **MEDIUM**: Service classes, utilities, data transformations
   - **LOW**: Constants, simple data classes, UI renderers

### Step 4: Generate Test Plan Document

Create a markdown document at `docs/test-plan-<feature-name>.md` with:

#### Document Structure

```markdown
# Test Case Plan for Commit: <commit-message>

**Commit:** `<commit-hash>`  
**Author:** <author>  
**Date:** <date>  
**Message:** <commit-message>

---

## Summary

<Brief description of what the commit changes>

---

## 1. New Classes Requiring Tests (Added - A)

### 1.1 <Category Name>

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `ClassName` | `path/to/File.kt` | **HIGH** | ❌ No test | <Detailed description> |

**Test Scenarios:**

1. **ClassName**
   - Test scenario 1
   - Test scenario 2
   - ...

---

## 2. Modified Classes Requiring Test Updates (Modified - M)

### 2.1 <Category Name>

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `ClassName` | `path/to/File.kt` | **MEDIUM** | ✅ Has test | <What changed and what to verify> |

**Test Scenarios:**

1. **ClassName**
   - Verify new behavior
   - Test edge cases
   - ...

---

## 3. Deleted Classes

| Class | Path | Notes |
|-------|------|-------|
| `ClassName` | `path/to/File.kt` | <Why deleted, migration notes> |

---

## 4. Test Implementation Priority

### Phase 1: Critical Tests (HIGH Priority)

| # | Test Class | Target Class | Key Scenarios |
|---|------------|--------------|---------------|
| 1 | `ClassTest` | `Class` | Key scenarios |

### Phase 2: Important Tests (HIGH Priority)

...

### Phase 3: Medium Priority Tests

...

### Phase 4: Low Priority Tests

...

---

## 5. Test File Locations

```
src/test/kotlin/com/example/
├── package/
│   ├── ClassTest.kt
│   └── ...
```

---

## 6. Existing Tests to Verify

| Test File | Status | Notes |
|-----------|--------|-------|
| `ExistingTest.kt` | ✅ Modified | <What to verify> |

---

## 7. Test Coverage Summary

| Category | Total | Has Test | Needs Test |
|----------|-------|----------|------------|
| New Classes | X | Y | Z |
| Modified Classes | X | Y | Z |
| **Total** | **X** | **Y** | **Z** |

---

## 8. Test Patterns and Utilities

### Test Base Classes
- List applicable test base classes

### Test Data
- Test data file locations

### Mock Utilities
- Available mock utilities

---

## 9. Next Steps

1. ⬜ Review each class without test coverage
2. ⬜ Prioritize based on complexity and usage
3. ⬜ Create test cases following the project's test patterns
4. ⬜ Ensure integration tests cover the full workflow
5. ⬜ Update this document as tests are implemented
```

### Step 5: Add Detailed Comments

For each class in the test plan, include:

1. **Description**: What the class does, its responsibilities, and how it integrates
2. **Test Scenarios**: Specific test cases to write
3. **Priority**: HIGH/MEDIUM/LOW based on:
   - Business criticality
   - Code complexity
   - Usage frequency
   - Risk of regression

### Step 6: Identify Test Patterns

Look at existing tests in the project to identify:

1. **Test base classes** being used
2. **Naming conventions** for test files
3. **Test utilities** and fixtures available
4. **Mock patterns** used
5. **Assertion libraries** used

## Example Usage

```
User: review the latest commit
```

The skill will:
1. Run `git log -1` to get commit info
2. Run `git diff HEAD~1 --name-status` to get changed files
3. Read each source file
4. Generate `docs/test-plan-<feature>.md`
5. Include detailed comments and test scenarios for each class

## Output

The skill produces a comprehensive test plan document that:
- Lists all added/modified/deleted classes
- Provides detailed descriptions for each class
- Suggests specific test scenarios
- Prioritizes test implementation
- Shows test coverage summary
- Guides test file organization

## Notes

- Focus on source files, not test files (test files are the output, not input)
- Exclude build files, resources, and configuration from detailed analysis
- Check if tests already exist for modified files
- Consider integration tests for complex workflows
- Group related classes by package or feature
