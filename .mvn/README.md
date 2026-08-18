# Why this directory exists

Maven walks up from the POM it was invoked on until it finds a `.mvn` directory, and
uses that directory as `${maven.multiModuleProjectDirectory}`. This marker pins that
property to the project root, so a build invoked on a single module resolves it to the
same place a reactor-wide build does.

The `license-maven-plugin` configuration in `pom.xml` depends on this: its
`licenseUrlReplacements` map third-party license URLs onto the texts in
`<project root>/license`. Without this marker, a build invoked on a single module would
resolve the property to that module's own directory and fail to find them.

`maven.config` is intentionally empty — it exists only to give this directory content to
track. Do not put arguments in it casually: every line becomes a command-line argument
for every build.

Maven only interprets `maven.config`, `jvm.config`, `extensions.xml` and `wrapper/` in
here; this file is inert.
