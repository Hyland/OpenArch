# Analysis Scripts

This directory contains various scripts for analyzing test execution and managing test sharding strategies.

## Scripts Overview

### `generate_shards.py`
**Purpose**: Analyzes integration test execution times and generates balanced shard configurations.
- **Strategy**: Complete modules only (simplified approach)
- **Output**: Balanced shards for integration tests
- **Usage**: Run after test execution to analyze and optimize sharding

```bash
python3 tools/analysis-scripts/generate_shards.py --target-shards 4 --output integration-shards.json
```

### `surefire_analysis.py`
**Purpose**: General utility for analyzing Maven Surefire test reports.
- **Functionality**: Parses XML test reports and generates markdown summaries
- **Scope**: All test types (lightweight, integration)
- **Usage**: For debugging test execution and generating reports

### `monitor_shard_performance.py`
**Purpose**: Monitors GitHub Actions performance to validate sharding effectiveness.
- **Functionality**: Analyzes workflow run times via GitHub API
- **Usage**: After implementing sharding to measure performance gains

## Integration Test Sharding Strategy

The current integration test sharding uses a **simplified complete-module approach**:
- Each shard contains complete Maven modules only
- No sub-module or test class splitting
- Easy to maintain and understand
- Balanced execution times across up to 4 shards
- Every shard runs against the same `docker-compose.yml` stack

### How to update after regenerating shards

1. Run `generate_shards.py` (see usage above) to regenerate the shard configuration.
2. Commit the updated `integration-shards.json`; the workflow picks up every shard it contains automatically.

Configuration: `.github/workflows/integration-shards.json`
