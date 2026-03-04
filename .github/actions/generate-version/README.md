# Version Generation Action

A simpler versioning strategy that automatically increments the patch version for every build.

## How it works

- **Automatic Patch Increment**: Every build automatically increments the patch version
- **Base Version**: Configurable major.minor version (default: 2.1)
- **Branch-specific formatting**:
  - **Master branch**: Clean semantic version (e.g., `2.1.5`)
  - **Pull Request**: Semantic version + PR metadata (e.g., `2.1.5-PullRequest1234.8-17677917468`)
  - **Feature branches**: Semantic version + branch name + run ID (e.g., `2.1.5-feature_branch-12345`)

## Usage

```yaml
- name: Generate Version
  id: version
  uses: ./.github/actions/generate-version
  with:
    base-version: '2.1'  # Optional, defaults to '2.1'

- name: Use generated version
  run: |
    echo "Version: ${{ steps.version.outputs.version }}"
    echo "Semantic Version: ${{ steps.version.outputs.semantic-version }}"
```

## Manual Version Updates

### Incrementing Major or Minor Version

To manually increment the major or minor version, follow these steps:

#### Option 1: Update base-version in workflows (Recommended)

1. **For Minor Version Increment** (e.g., 2.1.x → 2.2.x):
   ```yaml
   # In both continuous_integration.yaml and test-and-scan.yml
   - name: Generate Version
     uses: ./.github/actions/generate-version
     with:
       base-version: '2.2'  # Changed from '2.1'
   ```

2. **For Major Version Increment** (e.g., 2.1.x → 3.0.x):
   ```yaml
   # In both continuous_integration.yaml and test-and-scan.yml
   - name: Generate Version
     uses: ./.github/actions/generate-version
     with:
       base-version: '3.0'  # Changed from '2.1'
   ```

#### Option 2: Create a manual tag

1. **Create and push a new tag** with the desired version:
   ```bash
   git tag 2.2.0  # For minor increment
   git push origin 2.2.0
   ```

2. **Update the base-version** in both workflows to match:
   ```yaml
   base-version: '2.2'
   ```

### Best Practices

1. **Coordinate Updates**: Always update the `base-version` in both workflow files simultaneously
2. **Testing**: Test the version generation in a feature branch before merging to master

### Version Output Examples

- **Master branch build**: `2.1.15`
- **PR #1234**: `2.1.15-PullRequest1234.8-17677917468`
- **Feature branch**: `2.1.15-feature_new_api-12345`
