# hxpr-community-internal-app Project

This project uses SpringBoot.

The Nuxeo Runtime bootstrap is done thanks to module `hxpr-runtime-springboot` (see its README).
This registers a SpringBoot listener and starts up the Nuxeo Runtime, looking up `META-INF/MANIFEST.MF` files in the classpath.

A mock REST endpoint is added by this module, available at http://localhost:8080/hello.

Native build is not currently supported.

## Running the application in dev mode

You can run the application in dev mode that enables live coding using:

```shell script
mvn spring-boot:run -Dspring-boot.run.profiles=default,local
```

> **_NOTE:_**  A Dev UI is available (with `info.page.debug.enabled`) at http://localhost:8080/

## Packaging and running the application

Packaging is done with a standard Maven build:

```shell script
mvn clean package
```

The application is then runnable using:

```shell script
java -jar target/hxpr-community-internal-app-<version>.jar
```

Replace `<version>` with the current project version (see `pom.xml`, e.g. `0.0.61-SNAPSHOT`).

## Local docker dependencies

A `docker-compose.yml` is provided to start the services required for local development (MongoDB and any other backing services):

```shell
docker compose up -d
```

Stop the stack with:

```shell
docker compose down
```

## Configuration

Application configuration lives in `src/main/resources/application.properties`. Common overrides include:

- `nuxeo.mongodb.server` — MongoDB host/port (default points to the local docker stack)
- `nuxeo.mongodb.dbname` — MongoDB database name
- `org.nuxeo.ecm.product.version` — Product version (set by CI to the build version)

User definitions for local testing are seeded from `src/main/resources/hxpr-users.yml`.

## CI/CD in HXPR Community Internal

The `Continuous Integration` workflow (`.github/workflows/continuous_integration.yaml`) is the entry point of the CI/CD pipeline. On every push or pull request to `main` it:

1. Compiles the project with Maven.
2. On push to `main`, runs the Maven release (`_ci/release.sh`) to produce a released version, tag the repository and publish the artifacts to the Hyland Nexus / GitHub Packages repository.
3. Starts the docker-compose stack and runs the server build/tests against MongoDB.
4. Builds a multi-architecture (`linux/amd64`, `linux/arm64`) Docker image for `hxpr-community-internal-app-jvm` and publishes it to `ghcr.io/hylandsoftware/`.

The version used for the Docker tag and for `org.nuxeo.ecm.product.version` is derived from the `pom.xml` version (with `-SNAPSHOT` stripped on release builds), so it always matches the version produced by `_ci/release.sh`.

## Running the Docker image

After running the `docker compose up -d` command, you can run the application in a Docker container with (version 0.0.62 for this example):

```shell
docker run --rm -d -p 8080:8080 --name=HXPR_APP --network="host" \
  -e NUXEO_MONGODB_SERVER=localhost:27017 \
  -e NUXEO_DATA_DIR=/tmp \
  ghcr.io/hylandsoftware/hxpr-community-internal-app-jvm:0.0.62
```
