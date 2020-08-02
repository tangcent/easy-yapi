#!/usr/bin/env bash
#

SOURCE="$0"
while [[ -L "$SOURCE" ]]; do # resolve $SOURCE until the file is no longer a symlink
  scriptDir="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ ${SOURCE} != /* ]] && SOURCE="$scriptDir/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
scriptDir="$(cd -P "$(dirname "$SOURCE")" && pwd)"
basedir=${scriptDir%/*}
echo "baseDir:"${basedir}

version=$(cat ${basedir}/build.gradle | grep -Eo '[0-9][0-9.]+')
echo "version:"${version}
export DEPLOY_VERSION=version
export TRAVIS_TAG="${version}-patch"
#curl \
#  -X DELETE \
#  -H "Accept: application/vnd.github.v3+json" \
#  -H "authorization: Bearer ${GITHUB_TOKEN}" \
#  https://api.github.com/repos/tangcent/easy-api/releases/$TRAVIS_TAG

git tag -a $TRAVIS_TAG -m "Auto deployment"
export TRAVIS_TAG_TITLE="Auto deployment ${version}-SNAPSHOT"

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  export TRAVIS_TAG_BODY="patch with [#${TRAVIS_PULL_REQUEST}](https://github.com/${TRAVIS_REPO_SLUG}/pull/${TRAVIS_PULL_REQUEST})"
else
  export TRAVIS_TAG_BODY="patch with [#${TRAVIS_COMMIT_MESSAGE}](https://github.com/${TRAVIS_REPO_SLUG}/commit/${TRAVIS_COMMIT})"
fi
echo "title:"${TRAVIS_TAG_TITLE}
echo "body:"${TRAVIS_TAG_BODY}
echo ${TRAVIS_TAG} > tag.txt
echo ${TRAVIS_TAG_TITLE} > title.txt
echo ${TRAVIS_TAG_BODY} > body.txt