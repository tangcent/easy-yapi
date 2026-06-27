#!/usr/bin/env bash
# list_rule_files.sh — mirror of RuleFileResolver.listRuleFiles()
#
# Lists every rule file (.properties / .rules) in:
#   1. ~/.easyapi/              (global rules)
#   2. <project>/.easyapi/       (project rules)
#
# Project root is detected by walking up from $PWD looking for .easyapi/,
# .git/, build.gradle, or pom.xml.
#
# Usage:  ./list_rule_files.sh
# Output: one file path per line, sorted, prefixed with [global] or [project]
set -euo pipefail

# ── locate project root ──────────────────────────────────────────────────
find_project_root() {
    local dir="$PWD"
    while [ "$dir" != "/" ]; do
        if [ -d "$dir/.easyapi" ] || [ -d "$dir/.git" ] || \
           [ -f "$dir/build.gradle" ] || [ -f "$dir/build.gradle.kts" ] || \
           [ -f "$dir/pom.xml" ]; then
            printf '%s\n' "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    return 1
}

GLOBAL_DIR="$HOME/.easyapi"
PROJECT_ROOT="$(find_project_root || true)"
PROJECT_DIR=""
[ -n "$PROJECT_ROOT" ] && PROJECT_DIR="$PROJECT_ROOT/.easyapi"

# ── collect files ────────────────────────────────────────────────────────
list_dir() {
    local label="$1" dir="$2"
    [ -d "$dir" ] || return 0
    # shellcheck disable=SC2012
    ls -1 "$dir" 2>/dev/null | grep -E '\.(properties|rules)$' | while IFS= read -r f; do
        printf '[%s] %s/%s\n' "$label" "$dir" "$f"
    done
}

list_dir "global"  "$GLOBAL_DIR"
list_dir "project" "$PROJECT_DIR"
