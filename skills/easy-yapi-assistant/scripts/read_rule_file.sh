#!/usr/bin/env bash
# read_rule_file.sh — mirror of ReadRuleFileTool + RuleFileResolver.resolveByName()
#
# Read a rule file by NAME, resolving against tracked .easyapi/ folders.
# Supports scope prefixes:
#   global:<name>   — search only ~/.easyapi/
#   project:<name>  — search only <project>/.easyapi/
#   <name>          — search global first, then project (priority order)
#
# <name> must be a bare filename (no path separators).
#
# Usage:  ./read_rule_file.sh <name>
#   ./read_rule_file.sh security.properties
#   ./read_rule_file.sh global:jwt.rules
#   ./read_rule_file.sh project:custom.rules
set -euo pipefail

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
    echo "Usage: $0 <name>" >&2
    echo "  name may be prefixed with global: or project:" >&2
    exit 1
fi
REQUESTED="$1"

# ── parse scope ──────────────────────────────────────────────────────────
SCOPE="any"
NAME="$REQUESTED"
case "$REQUESTED" in
    global:*)  SCOPE="global";  NAME="${REQUESTED#global:}" ;;
    project:*) SCOPE="project"; NAME="${REQUESTED#project:}" ;;
esac

# Reject names containing path separators (mirrors RuleFileResolver guard).
case "$NAME" in
    */*|*\\*) echo "error: name must be a bare filename (no path separators): $NAME" >&2; exit 1 ;;
esac
[ -n "$NAME" ] || { echo "error: empty name after scope prefix" >&2; exit 1; }

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

# ── resolve ──────────────────────────────────────────────────────────────
try_dir() {
    local dir="$1"
    [ -d "$dir" ] || return 1
    local candidate="$dir/$NAME"
    if [ -f "$candidate" ]; then
        cat "$candidate"
        return 0
    fi
    return 1
}

case "$SCOPE" in
    global)  try_dir "$GLOBAL_DIR"  || { echo "error: not found in global: $NAME" >&2; exit 1; } ;;
    project) try_dir "$PROJECT_DIR" || { echo "error: not found in project: $NAME" >&2; exit 1; } ;;
    any)
        try_dir "$GLOBAL_DIR"  && exit 0
        try_dir "$PROJECT_DIR" && exit 0
        echo "error: rule file not found in any tracked dir: $NAME" >&2
        echo "  searched: $GLOBAL_DIR, ${PROJECT_DIR:-<no project root>}" >&2
        exit 1
        ;;
esac
