# Bruno Tests

This folder contains the Bruno collection for running end-to-end tests against the OpenArch application.

## Installation

### Bruno App

Download and install the Bruno desktop application from the [official website](https://www.usebruno.com/downloads) or via your package manager:

```bash
# macOS (Homebrew)
brew install bruno

# Linux (Snap)
snap install bruno

# Linux (Flatpak)
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install Bruno

# Windows (Winget)
winget install Bruno.Bruno

# Windows (Chocolatey)
choco install bruno
```

### Bruno CLI

The Bruno CLI (`@usebruno/cli`) is required to run tests from the command line (e.g. in CI). Install it globally via npm:

```bash
npm install -g @usebruno/cli
```

Verify the installation:

```bash
bru --version
```

## Usage

To import the collection into the Bruno App:

1. Open the Bruno desktop application.
2. Click **Open Collection** from the home screen (or **File → Open Collection** from the menu).
3. Navigate to and select the `ftests/bruno-tests` directory.
4. The **OpenArch Tests** collection will appear in the left sidebar with all requests listed in order.
5. Select the `openarch.localhost` environment from the environment dropdown (top-right corner) to load the default variables.

## Running Tests

### Prerequisites

Make sure the OpenArch application is running and accessible at `http://localhost:8080` before executing the tests. You can start it locally using Docker:

```bash
docker run --rm -d -p 8080:8080 --name=OPENARCH_APP \
  -e NUXEO_MONGODB_SERVER=localhost:27017 \
  -e NUXEO_DATA_DIR=/tmp \
  ghcr.io/hyland/openarch-app-jvm:<version>
```

### Run the full collection

From the `ftests/bruno-tests` directory, run all requests in sequence using the Bruno CLI:

```bash
bru run --env-file environments/openarch.localhost.json
```

### Run with an HTML report

```bash
bru run --env-file environments/openarch.localhost.json \
  --reporter-html localTestResults/bruno_test_report.html
```

The report will be generated at `localTestResults/bruno_test_report.html`.

### Run a single request

```bash
bru run get-health-check.bru --env-file environments/openarch.localhost.json
```
