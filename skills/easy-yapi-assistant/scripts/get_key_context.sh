#!/usr/bin/env bash
# get_key_context.sh — CLI mirror of the in-plugin GetRuleContextTool
#
# Fetches the runtime script-context for one rule key from the auto-generated
# `rule-contexts.md` catalog (mirror of the built-in `get_rule_context(key=...)`):
# the key's execution mode, per-key bindings, and the callable script-object
# APIs for that key.
#
# The catalog is generated from the plugin's *real* reflected script wrappers by
# the `syncRuleContexts` Gradle task, so the external assistant authors Groovy/
# Postman snippets against the exact same object API the in-plugin agent sees.
# The per-key section lists object ids; this script also expands the full method
# signatures for those ids from the catalog's shared `Script-Object API Reference`
# (they are stored once to keep the catalog small).
#
# A "script-context" is DIFFERENT from a key's value scheme (see rule-keys.md)
# and from its per-key guide (see get_key_guide.sh). Use this when you are about
# to author a script value and need the real callable object methods.
#
# Usage:  ./get_key_context.sh <key>
#   ./get_key_context.sh postman.test
#   ./get_key_context.sh api.class.parse.after
#
# Unknown key → "error: unknown rule key: <key>" on stderr, exit 1.
set -euo pipefail

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
    echo "Usage: $0 <key>" >&2
    echo "  List all supported keys from the bundled rule-keys.md." >&2
    exit 1
fi

KEY="$1"

# ── locate the auto-generated rule-contexts.md catalog ─────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CATALOG="$SCRIPT_DIR/../rule-contexts.md"

if [ ! -f "$CATALOG" ]; then
    echo "error: rule-contexts catalog not found at: $CATALOG" >&2
    echo "  Run ./gradlew syncRuleContexts from the easy-yapi repo to generate it." >&2
    exit 1
fi

# ── extract the `## <key>` section ─────────────────────────────────────────
# awk string equality (not regex) so dotted keys like `postman.test` match literally.
KEY_SECTION="$(
    awk -v key="$KEY" '
        $0 == "## " key { f = 1; print; next }
        f && /^## / { exit }
        f { print }
    ' "$CATALOG"
)"
if [ -z "$KEY_SECTION" ]; then
    echo "error: unknown rule key: $KEY" >&2
    echo "  Run ./gradlew syncRuleContexts; list keys from rule-keys.md." >&2
    exit 1
fi

echo "$KEY_SECTION"

# ── expand the bound object-API blocks from the shared reference ────────────
# Any backtick identifier in the key section that has a `### object: <id>` block
# is an object whose full method signatures live in the shared reference.
IDS="$(
    printf '%s\n' "$KEY_SECTION" \
        | grep -oE '`[A-Za-z_][A-Za-z0-9_.]*`' \
        | tr -d '`' | sort -u
)"
if [ -n "$IDS" ]; then
    echo ""
    echo "### Bound script-object APIs (methods):"
    for id in $IDS; do
        awk -v id="$id" '
            $0 == ("### object: " id) { f = 1; print; next }
            f && /^### object: / { exit }
            f { print }
        ' "$CATALOG"
    done
fi