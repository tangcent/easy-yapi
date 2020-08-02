#!/usr/bin/env bash
#

SOURCE="$0"
while [[ -h "$SOURCE"  ]]; do # resolve $SOURCE until the file is no longer a symlink
    scriptDir="$( cd -P "$( dirname "$SOURCE"  )" && pwd  )"
    SOURCE="$(readlink "$SOURCE")"
    [[ ${SOURCE} != /*  ]] && SOURCE="$scriptDir/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
scriptDir="$( cd -P "$( dirname "$SOURCE"  )" && pwd  )"
basedir=${scriptDir%/*}
echo "baseDir:"${basedir}

cd ${basedir}/idea-plugin
../gradlew clean buildPlugin --stacktrace
