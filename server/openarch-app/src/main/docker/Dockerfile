####
# This Dockerfile is used in order to build a container that runs the SpringBoot application in JVM mode
#
# Before building the container image run:
#
# ./mvnw package
#
# Then, build the image with:
#
# docker build -f src/main/docker/Dockerfile -t hyland/openarch-app-jvm .
#
# Then run the container using:
#
# docker run -i --rm -p 8080:8080 hyland/openarch-app-jvm
#
# If you want to include the debug port into your docker image
# you will have to expose the debug port (default 5005) like this :  EXPOSE 8080 5005
#
# Then run the container using :
#
# docker run -i --rm -p 8080:8080 -p 5005:5005 -e JAVA_ENABLE_DEBUG="true" hyland/openarch-app-jvm
#
# For current base image, see also: https://hub.docker.com/_/alpine/tags
#
###
# kics-scan disable=b03a748a-542d-44f4-bb86-9199ab4fd2d5
FROM amazoncorretto:25-alpine3.23@sha256:40938dac47cb8161e397f12d5ee2302a6190a005c59c28ab4b3b714d55c82388 AS builder

ARG RUN_JAVA_VERSION=1.3.8

# Install run-java script. curl is pinned and immediately removed to keep the layer clean.
RUN apk add --no-cache curl=8.23.0-r0 \
    && mkdir /deployments \
    && curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh \
         -o /deployments/run-java.sh \
    && apk del curl

# Stage BusyBox with required applet symlinks for the minimal runtime image.
# BusyBox is a single musl-linked binary; symlinking applets lets run-java.sh use grep/sed/awk/etc
# without any package manager in the runtime stage.
RUN mkdir -p /staging/bin \
    && cp /bin/busybox /staging/bin/busybox \
    && for cmd in sh grep sed awk wc ls cat head expr which dirname; do \
         ln -s /bin/busybox /staging/bin/$cmd; \
       done

# Set default user information
ARG GROUP_NAME=hyland
ARG GROUP_ID=1000
ARG USER_NAME=openarch-user
ARG USER_ID=33010

COPY target/openarch-app-*.jar /deployments/app.jar
COPY target/licenses /deployments/licenses
COPY target/*-3rd-Party-libraries.csv /deployments/licenses/

RUN addgroup -g ${GROUP_ID} ${GROUP_NAME} && \
    adduser -D -u ${USER_ID} -G ${GROUP_NAME} ${USER_NAME} && \
    mkdir -p /deployments/nxserver/data && \
    chown -R ${USER_NAME}:${GROUP_NAME} /deployments/nxserver && \
    chgrp -R ${GROUP_NAME} /deployments && \
    chmod +x /deployments/run-java.sh && \
    chmod 640 /deployments/app.jar && \
    sed -i 's/#networkaddress.cache.ttl=-1/networkaddress.cache.ttl=0/' $JAVA_HOME/conf/security/java.security

# kics-scan disable=b03a748a-542d-44f4-bb86-9199ab4fd2d5
FROM amazoncorretto:25-alpine3.23@sha256:40938dac47cb8161e397f12d5ee2302a6190a005c59c28ab4b3b714d55c82388

ARG BUILD_DATE
ARG COMMIT_SHA
ARG BUILD_URL

LABEL org.opencontainers.image.title="OpenArch" \
 org.opencontainers.image.source="https://github.com/Hyland/OpenArch" \
 org.opencontainers.image.created=$BUILD_DATE \
 org.opencontainers.image.revision=$COMMIT_SHA \
 org.opencontainers.image.url=$BUILD_URL

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# JAVA_APP_JAR tells run-java.sh exactly which jar to launch, skipping auto-detection.
# JAVA_OPTIONS is merged by run-java.sh alongside its auto-computed heap/GC flags.
ENV JAVA_APP_JAR="/deployments/app.jar" \
    JAVA_OPTIONS="-Daws.crt.libc=musl"

COPY --from=builder /deployments /deployments
COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group
# Copy BusyBox and its applet symlinks so run-java.sh can use grep/sed/awk/etc
# in the minimal runtime image without requiring any installed packages.
COPY --from=builder /staging/bin /bin
COPY --from=builder $JAVA_HOME/conf/security/java.security $JAVA_HOME/conf/security/java.security

EXPOSE 8080

USER openarch-user

ENTRYPOINT ["/bin/sh", "/deployments/run-java.sh"]
