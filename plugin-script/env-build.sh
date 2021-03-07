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

rm env.sh
echo -e "#!/usr/bin/env bash\n#\n" >> env.sh
echo -e "#\n" >> env.sh
echo -e "basedir=\"${basedir}\"" >> env.sh
cat env.sh.base >> env.sh
chmod +x env.sh
mv env.sh ${basedir}/envs
#source ${basedir}/envs/env.sh
