#!/usr/bin/env bash

SOURCE="$0"
while [[ -h "$SOURCE"  ]]; do
    scriptDir="$( cd -P "$( dirname "$SOURCE"  )" && pwd  )"
    SOURCE="$(readlink "$SOURCE")"
    [[ ${SOURCE} != /*  ]] && SOURCE="$scriptDir/$SOURCE"
done
scriptDir="$( cd -P "$( dirname "$SOURCE"  )" && pwd  )"
basedir=${scriptDir%/*}
cd "${basedir}"

# Optional first positional argument selects the IDEA compatibility range:
#   ./script/package.sh              # default: sinceBuild from gradle.properties (252), unbounded
#   ./script/package.sh '221-*'      # 2022.1 (221) and newer, unbounded — quote ranges containing *
#   ./script/package.sh 221-251     # 2022.1 (221) through 2025.1 (251)
#   ./script/package.sh '*-*'        # default lower bound, unbounded
# Only the default range (gradle.properties pluginSinceBuild) is officially
# supported; other ranges are best-effort builds for users on older IDEA
# versions — compatibility issues there are on the builder, not the project.
# Any other arguments are passed through to Gradle.
RANGE=""
GRADLE_ARGS=()
RANGE_PATTERN='^(\*|[0-9]+(\.[0-9]+)?)-(\*|[0-9]+(\.[0-9]+)?)?$'
for arg in "$@"; do
    if [[ -z "$RANGE" && "$arg" != -* && "$arg" =~ $RANGE_PATTERN ]]; then
        RANGE="$arg"
    else
        GRADLE_ARGS+=("$arg")
    fi
done

DEFAULT_SINCE="$(sed -n 's/^pluginSinceBuild=//p' gradle.properties | head -1 | tr -d '[:space:]')"
DEFAULT_SINCE="${DEFAULT_SINCE:-252}"

SINCE="$DEFAULT_SINCE"
UNTIL=""
if [[ -n "$RANGE" ]]; then
    SINCE="${RANGE%%-*}"
    UNTIL="${RANGE#*-}"
    [[ "$SINCE" == "*" ]] && SINCE="$DEFAULT_SINCE"
    [[ "$UNTIL" == "*" ]] && UNTIL=""
fi

GRADLE_ARGS+=("-PpluginSinceBuild=$SINCE")
[[ -n "$UNTIL" ]] && GRADLE_ARGS+=("-PpluginUntilBuild=$UNTIL")

echo "Packaging EasyYapi for IDEA builds: since=$SINCE until=${UNTIL:-<unbounded>}"

./gradlew clean buildPlugin "${GRADLE_ARGS[@]}"

pluginDir="${basedir}/plugin"
mkdir -p "${pluginDir}"

artifact=$(ls build/distributions/*.zip 2>/dev/null | head -1)
if [[ -n "$artifact" ]]; then
    mv "$artifact" "${pluginDir}/"
    echo "Artifact moved to ${pluginDir}/$(basename "$artifact")"
else
    echo "No artifact found in build/distributions/"
    exit 1
fi
