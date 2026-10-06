# Contributing to Hyland OpenArch

First off, thank you for considering contributing to Hyland OpenArch! It's people like you that make Hyland OpenArch a great tool for the community.

Following these guidelines helps communicate that you respect the time of the developers managing and developing this open source project. In return, they should reciprocate that respect in addressing your issue, assessing changes, and helping you finalize your pull requests.

This project and everyone participating in it is governed by our [Code of Conduct](https://github.com/Hyland/OpenArch/blob/main/CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## Contributor License Agreement (CLA)

Before Hyland can accept any contribution (code, tests, or documentation),
you must sign Hyland's Contributor License Agreement (CLA), modeled on the
Apache Software Foundation's CLA. This grants Hyland the rights needed to
distribute your contribution while you retain ownership of your work.

> **Note:** The CLA signing process is currently being designed.
> Please do not submit a pull request containing substantial contributions
> until the CLA process is available.

## What We're Looking For
We love contributions from our community! Here are some ways you can help:

### Types of Contributions We Welcome
- Bug Reports: Found a bug? [Let us know](https://github.com/Hyland/OpenArch/issues)!
- Feature Requests: Have an idea to [improve Hyland OpenArch](https://github.com/Hyland/OpenArch/discussions)? We'd love to hear it!
- Documentation: Help us improve or translate our [documentation](https://docs.hyland.com/r/Current/Hyland-Content-REST-API)
- Test Cases: Add test coverage for existing features
- Bug Fixes: Submit a fix for a [known issue](https://github.com/Hyland/OpenArch/issues)
- New Features: Implement functionality that benefits the community
- Examples & Tutorials: Show others how to use Hyland OpenArch

### What We're NOT Looking For (At This Time)
- Large architectural changes without prior discussion
- Features that only benefit a single use case
- Breaking changes to existing APIs without strong justification
- Code that doesn't follow our coding standards

## Getting Started
### Prerequisites
Before you begin, ensure you have:
- A Java Development Kit: JDK 25, Oracle's JDK or OpenJDK.
- Apache Maven 3.8.1+
- Familiarity with Spring Boot and REST API concepts
- A GitHub account

### Get the Source Code
Fork on GitHub, then clone your fork
`git clone https://github.com/YOUR-USERNAME/OpenArch.git`
`cd OpenArch`

### Building and Packaging
Export the relevant options for the JVM running Maven:

```shell
export MAVEN_OPTS='-Xmx4g -Xms2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
```

To build all modules, and the application, skipping tests, run:

```shell
mvn install -DskipTests
```

### Run Tests
Run the lightweight unit test suite (fast, no external services required):

```shell
mvn clean test -f modules -Dlightweight-test-suite
```

To also run the integration tests, start MongoDB first:

```shell
docker compose -f server/openarch-app/docker-compose.yml up -d mongodb
```

Then run:

```shell
mvn test -Dnuxeo.test.core=mongodb -Dnuxeo.mongodb.server=localhost:27017 -Dnuxeo.mongodb.dbname=unittests
```

### Start Local Development Instance
Start the required services:

```shell
docker compose -f server/openarch-app/docker-compose.yml up -d mongodb opensearch
```

Build the modules and then start the application:

```shell
mvn clean install -DskipTests
mvn spring-boot:run -pl server/openarch-app
```

### Verify Installation
Open [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) in your browser to confirm the API endpoint list loads.

You can also fetch the root folder with curl:

```shell
curl --request GET \
  --user admin:password \
  --url http://localhost:8080/api/documents/00000000-0000-0000-0000-000000000000
```

## How to Report a Bug

### Before Submitting a Bug Report
- Check existing issues: Search the [issues list](https://github.com/Hyland/OpenArch/issues) to see if the bug has already been reported
- Update to latest version: Verify the bug still exists in the most recent release
- Reproduce consistently: Ensure you can reliably reproduce the bug

### Submitting a Good Bug Report
Bug reports can be submitted through the project's [issues](https://github.com/Hyland/OpenArch/issues)

A good bug report includes:
- Clear title: Summarize the issue in one line
- Environment details:
    - Hyland OpenArch version
    - Operating system
    - Java/Runtime version
    - Database version
    - OpenSearch version
- Steps to reproduce
- Expected behavior: What should happen
- Actual behavior: What actually happens
- Logs/Screenshots: Include relevant error messages or screenshots
- Additional context: Any other relevant information

## How to Suggest a Feature
We love feature ideas from the community! Before suggesting a feature:

- Check [existing requests](https://github.com/Hyland/OpenArch/issues) for similar ideas
- Consider scope: Does this benefit the broader community?
- Think about maintenance: Can the feature be maintained long-term?

### Submitting a Feature Request
Feature requests can be submitted through the project's [discussions](https://github.com/Hyland/OpenArch/discussions)

A good feature request includes:

- Clear title: Summarize the feature in one line
- Problem statement: What problem does this solve?
- Proposed solution: How should it work?
- Alternatives considered: What other approaches did you think about?
- Use cases: Real-world scenarios where this would be valuable
- Willingness to contribute: Can you help implement this?

## How to Submit a Pull Request

### Before You Start Coding
- Open an issue first (for non-trivial changes) to discuss your approach
- Check for existing work: Search PRs to ensure you're not duplicating effort
- Review our coding standards
- Ensure you can sign our CLA

### The Pull Request Process

#### Create a feature branch
`git checkout -b feature/your-feature-name`
OR
`git checkout -b fix/your-bug-fix`

- Make your changes
- Write clean, readable code
- Follow our coding standards
- Add/update tests
- Update documentation
- Test thoroughly: all tests must pass
- Add new tests for new functionality
- Aim for >80% code coverage on new code
- Commit your changes

```
git add .
git commit -m "feat: add new feature X"
```

Types:
- feat: New feature
- fix: Bug fix
- docs: Documentation changes
- style: Code style changes (formatting, etc.)
- refactor: Code refactoring
- test: Adding or updating tests
- chore: Maintenance tasks

Example:

feat: add bulk document upload API

Implements a new REST endpoint that allows uploading multiple documents in a single request. This improves performance for batch operations by reducing network overhead.

Closes #123

- Push to your fork
`git push origin feature/your-feature-name`

- Open a Pull Request
- Go to [the Hyland OpenArch repository](https://github.com/Hyland/OpenArch) on GitHub
- Click "New Pull Request"
- Select your branch
- Fill out the PR template

```
## Description
[Clear description of what this PR does]

## Related Issue
Closes #[issue number]

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
[Describe the tests you ran and how to reproduce them]

## Checklist
- [ ] My code follows the project's coding standards
- [ ] I have performed a self-review of my code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have updated the documentation accordingly
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] I have signed the CLA (if required)
```

## Code Review Process
What Happens After You Submit a PR?

- We will ask you to sign the CLA if needed
- CI/CD pipeline runs automated tests
- Code quality checks (linting, formatting)
- Security scans
- Code review starts in parallel

### Code Review

We will be aiming to give you a first answer within 2 to 3 business days.

Then a maintainer will review your code. They may request changes or ask questions: please be as responsive as you can to feedback, and we will aim to do the same.

### Revision (if needed)

- Address reviewer comments
- Push additional commits to the same branch
- Re-request review when ready

### Approval & Merge (after approval)
Once approved, a maintainer will merge your PR and your contribution will be included in the next release! 🎉

## What We Look For in Code Review
- Functionality: Does it work as intended?
- Tests: Are there adequate tests?
- Code Quality: Is it readable and maintainable?
- Documentation: Is it properly documented?
- Standards: Does it follow our coding standards?
- Security: Are there any security concerns?
- Performance: Are there performance implications?

## Response Time Expectations
- Initial review: within 2-3 business days
- Follow-up reviews: within 1-2 business days
- Questions on existing PRs: within 1 business day

If you haven't heard back within these timeframes, feel free to politely ping the reviewers.

## Coding Standards

### General Principles
- Keep it simple: Prefer clarity over cleverness
- Code explains what it does, comments explain why
- Write tests: All new code should have tests
- Document public APIs: All public methods/classes should be documented
- Follow existing patterns: Be consistent with the existing codebase
- No tabs or trailing spaces
- No wildcard imports

### Code Style
The code style should match the existing codebase.

For IntelliJ IDEA, automatic formatting can be set up by:
- Installing the [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) plugin
- Importing the formatter config from `tools/openarch_formatter.xml`
- Configuring the import order from `tools/openarch.importorder`

### Testing Standards
- Unit Tests: test individual components in isolation
- Integration Tests: test component interactions
- Coverage Target: aim for >80% coverage on new code
- Test Naming: use descriptive names that explain what is being tested

### Documentation Standards
- Public APIs must be documented
- Complex algorithms should have explanatory comments
- README files for modules/packages

## Community

### Where to Get Help
- Discussions: [GitHub Discussions](https://github.com/Hyland/OpenArch/discussions)
- Community Forum: [Hyland Community](https://community.hyland.com)
- Stack Overflow: Tag questions with [hyland-openarch]

### Communication Channels
- For questions: [GitHub Discussions](https://github.com/Hyland/OpenArch/discussions)
- For bugs: [GitHub issues](https://github.com/Hyland/OpenArch/issues)
- For security issues: See [Hyland OpenArch Security](https://github.com/Hyland/OpenArch/security)
- For general discussion: [GitHub Discussions](https://github.com/Hyland/OpenArch/discussions)

### Recognition
All contributors will be:
- Listed in our CONTRIBUTORS.md file
- Mentioned in release notes (for significant contributions)
- Part of making Hyland OpenArch better for everyone!

### Questions?
If you have questions that aren't covered in this guide, please start a new [discussion](https://github.com/Hyland/OpenArch/discussions).

### License
By contributing to Hyland OpenArch, you agree that your contributions will be licensed under the Apache 2.0 license.

Thank you for contributing to Hyland OpenArch!
