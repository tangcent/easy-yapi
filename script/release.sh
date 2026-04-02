#!/usr/bin/env bash

set -e

SCRIPT_SOURCE="$0"
while [[ -h "$SCRIPT_SOURCE" ]]; do
    scriptDir="$( cd -P "$( dirname "$SCRIPT_SOURCE" )" && pwd )"
    SCRIPT_SOURCE="$(readlink "$SCRIPT_SOURCE")"
    [[ ${SCRIPT_SOURCE} != /* ]] && SCRIPT_SOURCE="$scriptDir/$SCRIPT_SOURCE"
done
scriptDir="$( cd -P "$( dirname "$SCRIPT_SOURCE" )" && pwd )"
basedir=${scriptDir%/*}
cd "${basedir}"

last_version=$(grep '^version\s*=' build.gradle.kts | sed -E 's/.*"([0-9]+\.[0-9]+\.[0-9]+).*/\1/')
echo "Last version: ${last_version}"

release_date=$(date +"%Y-%m-%d")

IFS='.' read -r major minor patch <<< "$last_version"
if [[ -z "$major" || -z "$minor" || -z "$patch" ]]; then
    echo "Error: Could not parse version from build.gradle.kts"
    exit 1
fi

if [[ "${patch}" == "9" ]]; then
    patch=0
    minor=$((minor + 1))
else
    patch=$((patch + 1))
fi

next_version="${major}.${minor}.${patch}"
echo "Next version: ${next_version}"

release_branch="release/v${next_version}"
git branch "${release_branch}"
git checkout "${release_branch}"

sed -i.bak "s/^version = \".*\"/version = \"${next_version}.252.0\"/" build.gradle.kts
rm -f build.gradle.kts.bak
echo "Updated version in build.gradle.kts to ${next_version}.252.0"

last_tag="v${last_version}"
if ! git rev-parse "${last_tag}" >/dev/null 2>&1; then
    echo "Warning: Tag ${last_tag} not found. Using all commits."
    commits_since=""
else
    commits_since="${last_tag}..HEAD"
fi

echo "Collecting commits since ${last_tag:-beginning}..."

feat_commits=$(git log --pretty=format:"%s" ${commits_since} 2>/dev/null | grep -E "^feat[:(\s]" | sed 's/^feat:[\s]*//' || true)
fix_commits=$(git log --pretty=format:"%s" ${commits_since} 2>/dev/null | grep -E "^fix[:(\s]" | sed 's/^fix:[\s]*//' || true)
refactor_commits=$(git log --pretty=format:"%s" ${commits_since} 2>/dev/null | grep -E "^refactor[:(\s]" | sed 's/^refactor:[\s]*//' || true)
other_commits=$(git log --pretty=format:"%s" ${commits_since} 2>/dev/null | grep -ivE "^(feat|fix|refactor)[:(\s]" | grep -vE "^(feat|fix|refactor)[:]" || true)

CHANGELOG="CHANGELOG.md"

{
    echo "## [${next_version}] - ${release_date}"
    echo ""
    if [[ -n "${feat_commits}" ]]; then
        echo "### Added"
        echo "${feat_commits}" | while IFS= read -r line; do echo "- ${line}"; done
        echo ""
    fi
    if [[ -n "${fix_commits}" ]]; then
        echo "### Fixed"
        echo "${fix_commits}" | while IFS= read -r line; do echo "- ${line}"; done
        echo ""
    fi
    if [[ -n "${refactor_commits}" ]]; then
        echo "### Changed"
        echo "${refactor_commits}" | while IFS= read -r line; do echo "- ${line}"; done
        echo ""
    fi
    if [[ -n "${other_commits}" ]]; then
        echo "### Improved"
        echo "${other_commits}" | while IFS= read -r line; do echo "- ${line}"; done
        echo ""
    fi
    echo "---"
    echo ""
} > .release_temp.md

if git rev-parse "${last_tag}" >/dev/null 2>&1; then
    line_num=$(grep -n "## \[${last_version}\]" "${CHANGELOG}" | head -1 | cut -d: -f1)
    if [[ -n "${line_num}" && "${line_num}" -gt 0 ]]; then
        head -n $((line_num - 1)) "${CHANGELOG}" > .changelog_head.md
        tail -n +$((line_num)) "${CHANGELOG}" > .changelog_tail.md
        cat .changelog_head.md .release_temp.md .changelog_tail.md > "${CHANGELOG}"
        rm -f .changelog_head.md .changelog_tail.md
    else
        cat .release_temp.md "${CHANGELOG}" > .changelog_new.md && mv .changelog_new.md "${CHANGELOG}"
    fi
else
    cat .release_temp.md "${CHANGELOG}" > .changelog_new.md && mv .changelog_new.md "${CHANGELOG}"
fi

rm -f .release_temp.md
echo "Updated CHANGELOG.md with release ${next_version}"

git add build.gradle.kts CHANGELOG.md

commit_message="release v${next_version}"
git commit -m "${commit_message}"
echo "Created commit: ${commit_message}"

echo ""
echo "Release ${next_version} prepared and committed on branch ${release_branch}"
echo ""
echo "Next steps:"
echo "  1. Push branch: git push origin ${release_branch}"
echo "  2. Create PR or merge to master"
echo "  3. On master push with 'release' in commit message to trigger GitHub Release"