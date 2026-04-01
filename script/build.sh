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

./gradlew clean build -x test "$@"

