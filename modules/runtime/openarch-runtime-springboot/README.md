# nuxeo-runtime-springboot Project

This project uses SpringBoot

The Nuxeo Runtime bootstrap is done thanks to a Spring listener and starts up the Nuxeo Runtime, looking up META-INF/MANIFEST.MF files in the classpath.

A Nuxeo Runtime health check is provided by this module, available at http://localhost:8080/actuator/health.

## Packaging

Packaging is done by using usual maven build.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
mvn spring-boot:run -Dspring-boot.run.profiles=default,local
```

> **_NOTE:_**  There is a Dev UI, which is available at http://localhost:8080/
