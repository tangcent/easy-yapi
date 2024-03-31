#!/bin/bash

# Set basedir to the root directory of the project
basedir=$(cd "$(dirname "$0")/.." && pwd)

# Get the last version
last_version=$(cat ${basedir}/gradle.properties | grep -Eo -m1 '[0-9]+\.[0-9]+\.[0-9]+')
echo "Last version: ${last_version}"

# Set release_date to today's date
release_date=$(date +"%Y-%m-%d")

# Calculate the next version
parts=(${last_version//./ })

if [[ ${parts[2]//[!0-9]/} == 9 ]]; then
  parts[2]=0
  parts[1]=$((parts[1] + 1))
else
  parts[2]=$((parts[2] + 1))
fi

next_version="${parts[0]}.${parts[1]}.${parts[2]}"
echo "Next version: ${next_version}"

# Create a new release branch based on the next version
release_branch="release/v${next_version}"
git branch ${release_branch}
git checkout ${release_branch}

# Get a list of all commits made after the last release tag
commits=$(git log --pretty=format:"%s" v${last_version}..HEAD | sed 's/\(#\([0-9]*\)\)/<a href="https:\/\/github.com\/tangcent\/easy-yapi\/pull\/\2">\1<\/a>/')
echo "commits:${commits}"

# Separate commits into enhancements and fixes
enhancements=$(echo "${commits}" | grep -i "^feat" | sed 's/^/<li>/;s/$/<\/li>/')
fixes=$(echo "${commits}" | grep -i "^fix" | sed 's/^/<li>/;s/$/<\/li>/')
others=$(echo "${commits}" | grep -ivE "^feat|^fix" | sed 's/^/<li>/;s/$/<\/li>/')


# Update the version number in the gradle.properties file
sed -i.bak "s/version=${last_version}/version=${next_version}/g" ${basedir}/gradle.properties && rm ${basedir}/gradle.properties.bak

# Update the version number in the plugin.xml
sed -i.bak "s/<version\>${last_version}/<version\>${next_version}/" ${basedir}/idea-plugin/src/main/resources/META-INF/plugin.xml && rm ${basedir}/idea-plugin/src/main/resources/META-INF/plugin.xml.bak

# Write the header to the pluginChanges.html file
echo "<a href=\"https://github.com/tangcent/easy-yapi/releases/tag/v${next_version}\">v${next_version}(${release_date})</a>" > ${basedir}/idea-plugin/parts/pluginChanges.html
echo "<br/>" >> ${basedir}/idea-plugin/parts/pluginChanges.html
echo "<a href=\"https://github.com/tangcent/easy-yapi/blob/master/IDEA_CHANGELOG.md\">Full Changelog</a>" >> ${basedir}/idea-plugin/parts/pluginChanges.html

echo "<h3>Enhancements:</h3>" >> ${basedir}/idea-plugin/parts/pluginChanges.html
# Write the list of enhancements to the pluginChanges.html file (if there are any)
if [ -n "${enhancements}" ]; then
  echo "<ul>${enhancements}</ul>" >> ${basedir}/idea-plugin/parts/pluginChanges.html
fi

echo "<h3>Fixes:</h3>" >> ${basedir}/idea-plugin/parts/pluginChanges.html
# Append the list of fixes to the pluginChanges.html file (if there are any)
if [ -n "${fixes}" ]; then
  echo "<ul>${fixes}</ul>" >> ${basedir}/idea-plugin/parts/pluginChanges.html
fi

# Append the list of other commits to the pluginChanges.html file (if there are any)
#if [ -n "${others}" ]; then
#  echo "<ul>other: ${others}</ul>" >> ${basedir}/idea-plugin/parts/pluginChanges.html
#fi

# Use tidy to prettify the HTML code in pluginChanges.html and only show the contents of the <body> element
tidy -q -indent -wrap 0 --show-body-only yes ${basedir}/idea-plugin/parts/pluginChanges.html > ${basedir}/idea-plugin/parts/pluginChanges_temp.html
mv ${basedir}/idea-plugin/parts/pluginChanges_temp.html ${basedir}/idea-plugin/parts/pluginChanges.html

commits_for_changes_log=$(git log --pretty=format:"%s" v${last_version}..HEAD | sed -e 's/^\(.*\)(#\([0-9]*\))$/\	* \1 \[\(#\2\)]\(https:\/\/github.com\/tangcent\/easy-yapi\/pull\/\2\)\n/')
echo "commits_for_changes_log:${commits_for_changes_log}"

echo "${commits_for_changes_log}" | cat - ${basedir}/IDEA_CHANGELOG.md > tmp && mv tmp ${basedir}/IDEA_CHANGELOG.md
echo "* ${next_version}" | cat - ${basedir}/IDEA_CHANGELOG.md > tmp && mv tmp ${basedir}/IDEA_CHANGELOG.md

# Add all changes and create a new commit
git add ${basedir}/gradle.properties ${basedir}/IDEA_CHANGELOG.md ${basedir}/idea-plugin/parts/pluginChanges.html ${basedir}/idea-plugin/src/main/resources/META-INF/plugin.xml

# Create a new commit with the list of commit messages
commit_message="release ${next_version}"

git commit -m "${commit_message}"