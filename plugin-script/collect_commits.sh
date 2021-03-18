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

#last_tag_commit=`git rev-list --tags --max-count=1`
#pre_tag=`git describe --tags $last_tag_commit`
#echo "pre tag:" $pre_tag
version=`cat ${basedir}/gradle.properties | grep -Eo -m1 '[0-9]\.[0-9]\.[0-9]'`
echo "version:"${version}
git fetch origin tag v${version}
git fetch origin master
revision=`git log refs/tags/v${version} -1 --pretty=%H`
master_revision=`git log origin/master -1 --pretty=%H`
echo "revision:"${revision}
echo "master_revision:"${master_revision}
echo git log --pretty=format:"%s" ${revision}...${master_revision}
commits=`git log --pretty=format:"%s" ${revision}...${master_revision}`
echo "$commits" > $scriptDir/commits.txt
echo "commits:"
cat $scriptDir/commits.txt