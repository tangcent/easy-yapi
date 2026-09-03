#!/usr/bin/env bash
# get_key_guide.sh — CLI mirror of the in-plugin GetRuleDetailTool (by-key path)
#
# Fetches the full per-key guide from the bundled `ai/key-guides/<key>.md`
# catalog file. The catalog is kept in sync with `src/main/resources/ai/key-guides/`
# by the `syncAgentCatalog` Gradle task. The YAML front-matter header is
# stripped; only the markdown body is printed to stdout — same contract as
# the in-plugin tool's by-key path (`get_rule_detail(key=...)`).
#
# A "key guide" is the built-in per-key documentation (value format, external
# runtime, workflow) for a rule key. This is DIFFERENT from the user's actual
# configured rules — to look those up use `get_existing_rules_for_key.sh`.
#
# Usage:  ./get_key_guide.sh <key>
#   ./get_key_guide.sh postman.test
#   ./get_key_guide.sh method.additional.header
#
# Unknown key → "error: unknown rule key: <key>" on stderr, exit 1.
set -euo pipefail

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
    echo "Usage: $0 <key>" >&2
    echo "  List available keys with: $0 --list" >&2
    echo "  (or read the full catalog in rule-keys.md)" >&2
    exit 1
fi

# ── --list shortcut ──────────────────────────────────────────────────────
# Lists every key that has a per-key guide file, as `key — title: cue` lines
# (same shape as the in-plugin key-guide index). The full key catalog —
# including keys with no guide file — is in rule-keys.md / rule-keys.json.
if [ "$1" = "--list" ]; then
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    GUIDES_DIR="$SCRIPT_DIR/../ai/key-guides"
    if [ ! -d "$GUIDES_DIR" ]; then
        echo "error: bundled catalog not found at: $GUIDES_DIR" >&2
        echo "  Run ./gradlew syncAgentCatalog from the easy-yapi repo to populate it." >&2
        exit 1
    fi
    shopt -s nullglob
    FILES=( "$GUIDES_DIR"/*.md )
    if [ ${#FILES[@]} -eq 0 ]; then
        echo "(no key-guide files in bundled catalog)" >&2
        exit 0
    fi
    for FILE in "${FILES[@]}"; do
        awk '
            /^---[[:space:]]*$/ {
                count++
                next
            }
            count == 1 {
                print
            }
        ' "$FILE" | awk -F': ' '
            /^key:/    { key = $0;   sub(/^key:[[:space:]]*/, "", key);   gsub(/^"|"$/, "", key) }
            /^title:/  { title = $0; sub(/^title:[[:space:]]*/, "", title); gsub(/^"|"$/, "", title) }
            /^cue:/    { cue = $0;   sub(/^cue:[[:space:]]*/, "", cue);   gsub(/^"|"$/, "", cue) }
            END {
                if (key != "" && title != "" && cue != "") {
                    printf "%s — %s: %s\n", key, title, cue
                }
            }
        '
    done
    exit 0
fi

KEY="$1"

# ── locate the bundled ai/key-guides/ folder ─────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GUIDES_DIR="$SCRIPT_DIR/../ai/key-guides"

if [ ! -d "$GUIDES_DIR" ]; then
    echo "error: bundled catalog not found at: $GUIDES_DIR" >&2
    echo "  Run ./gradlew syncAgentCatalog from the easy-yapi repo to populate it." >&2
    exit 1
fi

FILE="$GUIDES_DIR/$KEY.md"
if [ ! -f "$FILE" ]; then
    echo "error: unknown rule key: $KEY" >&2
    echo "  Available keys:" >&2
    ls -1 "$GUIDES_DIR" | sed 's/\.md$//' | sed 's/^/    /' >&2
    exit 1
fi

# ── strip the YAML front-matter (between the first two `---` lines) ──────
# The header is `---\n<yaml>\n---\n`; the body follows. Print everything
# after the second `---` line.
awk '
    /^---[[:space:]]*$/ {
        count++
        next
    }
    count >= 2 { print }
' "$FILE"
