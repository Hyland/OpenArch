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

PREPARE=""
if [[ ${RELEASE_PREPARE} = true ]]; then
    PREPARE="release:prepare"
fi

RELEASE=""
if [[ ${RELEASE_PERFORM} = true ]]; then
    RELEASE="release:perform"
fi

ARTIFACT_URL=""
if [[ -n ${DISTRIBUTION_REPO_URL} ]]; then
    ARTIFACT_URL="-Ddistribution-repository.url=${DISTRIBUTION_REPO_URL}"
fi

CLASSIFIERS=""
if [[ ${RELEASE_PERFORM_WITH_CLASSIFIER} = true && -n ${CLASSIFIER_PARAM} ]]; then
    CLASSIFIERS="${CLASSIFIER_PARAM}"
fi

# Run the release plugin - with "[skip ci]" in the release commit message
# We add the profiles build-all so that all modules are built and versions updated
mvn -B \
    -Dhttp.keepAlive=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -Prelease,build-all \
    "-Darguments=-Prelease ${CLASSIFIERS} ${ARTIFACT_URL} -DskipTests -Dmaven.javadoc.skip -Dadditionalparam=-Xdoclint:none" \
    release:clean ${PREPARE} ${RELEASE} \
    -DscmCommentPrefix="[maven-release-plugin][skip ci] " \
    -Dusername=${RELEASE_USERNAME} \
    -Dpassword=${RELEASE_USER_PAT}

popd
set +vex
echo "=========================== Finishing Release Script =========================="
