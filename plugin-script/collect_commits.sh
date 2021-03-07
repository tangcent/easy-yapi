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
version=`cat ${basedir}/build.gradle | grep -Eo -m1 '[0-9]\.[0-9]\.[0-9]'`
echo "version:"${version}
echo git fetch origin tag v${version}
git fetch origin tag v${version}
echo git fetch origin refs/tags/v${version}
git fetch origin refs/tags/v${version}
echo -e "tags:"`git show-ref --tags -d`
echo git log refs/tags/v${version} -1 --pretty=%H
revision=`git log refs/tags/v${version} -1 --pretty=%H`
echo git log --pretty=format:"%s" ...${revision}
commits=`git log --pretty=format:"%s" ...${revision}`
echo "$commits" > $scriptDir/commits.txt
echo "commits:"
cat $scriptDir/commits.txt