# Analysis Scripts

This directory contains various scripts for analyzing test execution and managing test sharding strategies.

## Scripts Overview

### `generate_shards.py`
**Purpose**: Analyzes integration test execution times and generates balanced shard configurations.
- **Strategy**: Complete modules only (simplified approach)
- **Output**: Balanced shards for integration tests (7 shards: 4 community + 3 enterprise)
- **Usage**: Run after test execution to analyze and optimize sharding

```bash
python3 development-files/analysis-scripts/generate_shards.py --target-shards 7 --output integration-shards.json
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
- Balanced execution times across 7 shards

### Common vs Enterprise split (shards 1-4 vs 5-7)

Shards are divided into two groups that map to different CI Docker stacks:

| Shards | Description | Docker stack used |
|--------|-------------|-------------------|
| 1 – 4 | Common modules (Community) | `docker-compose.community.yml` |
| 5 – 7 | Enterprise-only modules | `docker-compose.yml` (full stack with transform services & localstack) |

**Enterprise modules** are identified dynamically when they:
- Live under one of:
- `modules/core-enterprise/*`
- `modules/platform-enterprise/*`
- `modules/runtime-enterprise/*`
- And contain a `.integration-test-suite` marker file in that module directory.

This avoids maintaining a static enterprise module list in the script.

### How to update after regenerating shards

The `type` field in `integration-shards.json` is the source of truth for routing shards to the correct docker-compose stack.

1. Run `generate_shards.py` (see usage above) to generate the shard configuration with `type` fields.
2. When a module is added or moved, check its parent directory:
   - `modules/core-enterprise/*`      + `.integration-test-suite` marker → auto-classified as `type: "enterprise"`
   - `modules/platform-enterprise/*`  + `.integration-test-suite` marker → same
   - `modules/runtime-enterprise/*`   + `.integration-test-suite` marker → same
   - Everything else                   → automatically placed in common shards 1-4 with `type: "community"`
3. The generated `type` field in each shard definition will automatically route to the correct docker-compose stack in the workflow.

**Note**: The `type` field is generated dynamically from module path + marker detection, making shard routing clearer and less error-prone.

Configuration: `.github/workflows/integration-shards.json`
Documentation: `docs/TEST_SUITE_ARCHITECTURE.md`
