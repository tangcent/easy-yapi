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

./gradlew clean buildPlugin "$@"

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

