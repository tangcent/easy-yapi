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

bash $scriptDir/collect_commits.sh
./gradlew patchUpdate --stacktrace
rm $scriptDir/commits.txt

cd $scriptDir
./env-build.sh

cd ${basedir}
echo "swith to"`pwd`

git add .
version=`cat ${basedir}/gradle.properties | grep -Eo -m1 '[0-9]\.[0-9]\.[0-9]'`
echo "version:${version}"
git config user.name tangcent
git config user.email pentatangcent@gmail.com
git commit -m "release v$version"