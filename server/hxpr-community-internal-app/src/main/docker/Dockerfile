####
# This Dockerfile is used in order to build a container that runs the SpringBoot application in JVM mode
#
# Before building the container image run:
#
# ./mvnw package
#
# Then, build the image with:
#
# docker build -f src/main/docker/Dockerfile -t hyland/hxpr-community-internal-app-jvm .
#
# Then run the container using:
#
# docker run -i --rm -p 8080:8080 hyland/hxpr-community-internal-app-jvm
#
# If you want to include the debug port into your docker image
# you will have to expose the debug port (default 5005) like this :  EXPOSE 8080 5005
#
# Then run the container using :
#
# docker run -i --rm -p 8080:8080 -p 5005:5005 -e JAVA_ENABLE_DEBUG="true" hyland/hxpr-community-internal-app-jvm
#
# For current base image, see also: https://hub.docker.com/_/alpine/tags
#
###
FROM ghcr.io/hylandsoftware/corretto-sambuca-25:20260216@sha256:2c21b99b7ab961c40ef1b6d0e0c633190b39ba4fca74acdbc32f5badaeca766d

ARG RUN_JAVA_VERSION=1.3.8
ARG BUILD_DATE
ARG COMMIT_SHA
ARG BUILD_URL

LABEL org.opencontainers.image.title="Hyland Community Platform Repository" \
 org.opencontainers.image.source="https://github.com/HylandSoftware/hxpr-community-internal" \
 org.opencontainers.image.created=$BUILD_DATE \
 org.opencontainers.image.revision=$COMMIT_SHA \
 org.opencontainers.image.url=$BUILD_URL

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# Set default user information
ARG GROUP_NAME=Hyland
ARG GROUP_ID=1000
ARG USER_NAME=hxpr-app-user
ARG USER_ID=33010

# Install run-java script
RUN mkdir /deployments \
    && curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh -o /deployments/run-java.sh \
    && apk del curl

COPY target/hxpr-community-internal-app-*.jar /deployments/

RUN addgroup -g ${GROUP_ID} ${GROUP_NAME} && \
    adduser -D -u ${USER_ID} -G ${GROUP_NAME} ${USER_NAME} && \
    chgrp -R ${GROUP_NAME} /deployments && \
    chmod +x /deployments/run-java.sh

RUN sed -i 's/#networkaddress.cache.ttl=-1/networkaddress.cache.ttl=0/' $JAVA_HOME/conf/security/java.security

EXPOSE 8080

USER ${USER_NAME}

ENTRYPOINT [ "/deployments/run-java.sh" ]
