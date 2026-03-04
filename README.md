
# HXPR Community

This repository contains the HxP Repository (HXPR) Community implementation.

[![Continuous Integration](https://github.com/HylandSoftware/hxpr-community/actions/workflows/continuous_integration.yaml/badge.svg?branch=main)](https://github.com/HylandSoftware/hxpr-community/actions/workflows/continuous_integration.yaml?query=branch%3Amain)

## Installation

Running HXPR requires JDK 25, Oracle's JDK or OpenJDK.

## Building and Packaging

Building HXPR requires the following tools:

- Git (obviously)
- JDK 25 (Oracle's JDK or OpenJDK recommended)
- Apache Maven 3.8.1+

Get the source code:

```shell
git clone git@github.com:HylandSoftware/hxpr-community.git
cd hxpr-community
```

Export the relevant options for the JVM running Maven:

```shell
export MAVEN_OPTS='-Xmx4g -Xms2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
```

To build all modules, and the application, skipping tests, run:

```shell
mvn install -DskipTests
```
