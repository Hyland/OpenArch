#!/usr/bin/env bash

echo "=========================== Starting Release Script ==========================="
PS4="\[\e[35m\]+ \[\e[m\]"
set -vex
pushd "$(dirname "${BASH_SOURCE[0]}")/../"

echo "BRANCH_NAME=${BRANCH_NAME}"

# Travis CI runner work on DETACHED HEAD, so we need to checkout the release branch
git checkout -B "${BRANCH_NAME}"

git config user.name "${RELEASE_USERNAME}"
git config user.email "<${RELEASE_USER_EMAIL}>"

RELEASE=""
if [ ${PERFORM_RELEASE} = true ]; then
    RELEASE="release:perform"
fi

# Run the release plugin - with "[skip ci]" in the release commit message
# We add the profiles build-all so that all modules are built and versions updated
mvn -B \
    -Dhttp.keepAlive=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -Prelease,build-all \
    "-Darguments=-Prelease -DskipTests -Dmaven.javadoc.skip -Dadditionalparam=-Xdoclint:none" \
    release:clean release:prepare ${RELEASE} \
    -DscmCommentPrefix="[maven-release-plugin][skip ci] " \
    -Dusername=${RELEASE_USERNAME} \
    -Dpassword=${RELEASE_USER_PAT}

popd
set +vex
echo "=========================== Finishing Release Script =========================="
