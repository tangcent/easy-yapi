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
../gradlew clean buildPlugin -x test --stacktrace

version=`cat ${basedir}/gradle.properties | grep -Eo -m1 '[0-9][0-9.]+(-rc)?'`
echo "version:"${version}


if [[ ! -d "$basedir/plugin" ]];then
mkdir ${basedir}/plugin
fi
mv ${basedir}/idea-plugin/build/distributions/*.zip ${basedir}/plugin/easy-yapi-${version}.zip