#!/usr/bin/env bash
# get_existing_rules_for_key.sh — mirror of GetExistingRulesForKeyTool
#
# Find all configured values for one or more rule keys across project and
# global rule files. Shows the source file + line number + value so the
# assistant can reason about precedence (global < project; later lines win
# for "replace" mode, concatenate for "merge" mode — see rule-keys.md).
#
# Usage:  ./get_existing_rules_for_key.sh <key> [<key> ...]
#   ./get_existing_rules_for_key.sh field.name
#   ./get_existing_rules_for_key.sh api.tag method.additional.header
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <key> [<key> ...]" >&2
    exit 1
fi

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

# Collect all rule files (ordered: global first, project second — matches
# EasyApi precedence where project overrides global).
RULE_FILES=()
add_files_from() {
    local dir="$1"
    [ -d "$dir" ] || return 0
    while IFS= read -r f; do
        RULE_FILES+=("$f")
    done < <(ls -1 "$dir" 2>/dev/null | grep -E '\.(properties|rules)$' | while IFS= read -r name; do printf '%s/%s\n' "$dir" "$name"; done)
}
add_files_from "$GLOBAL_DIR"
add_files_from "$PROJECT_DIR"

if [ ${#RULE_FILES[@]} -eq 0 ]; then
    echo "(no rule files found in $GLOBAL_DIR or ${PROJECT_DIR:-<no project>})" >&2
    exit 0
fi

# ── search ──────────────────────────────────────────────────────────────
# A rule line looks like:  key[filter]=value   or   key=value
# We match lines where the key appears before the first '=' (and before any '[').
for KEY in "$@"; do
    echo "=== $KEY ==="
    found=0
    for f in "${RULE_FILES[@]}"; do
        # grep for lines where the part before [ or = matches the key (trimmed).
        # Use grep -n for line numbers, then filter precisely in awk.
        while IFS= read -r match; do
            lineno="${match%%:*}"
            rest="${match#*:}"
            # Extract the key portion: everything before '[' or '=', trimmed.
            key_part="$(printf '%s' "$rest" | sed -E 's/^([^=[\[]*).*/\1/' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
            if [ "$key_part" = "$KEY" ]; then
                echo "  $f:$lineno: $rest"
                found=1
            fi
        done < <(grep -nE "^${KEY}([[:space:]]*\[|([[:space:]]*)=)" "$f" 2>/dev/null || true)
    done
    [ "$found" -eq 0 ] && echo "  (not configured)"
done
