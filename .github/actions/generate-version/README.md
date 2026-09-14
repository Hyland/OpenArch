# Version Generation Action

This action keeps community versioning decoupled by reading the major/minor value from `VERSION` and deriving the patch from git history.

## How it works

- **Manual Major/Minor Control**: `VERSION` stores the current `MAJOR.MINOR` line
- **Automatic Patch Increment**: Every build uses the number of commits since `VERSION` was last updated as the patch
- **Branch-specific formatting**:
  - **Main branch**: Clean semantic version (e.g., `0.1.5`)
  - **Pull Request**: Semantic version + PR metadata (e.g., `0.1.5-PullRequest1234.8-17677917468`)
  - **Feature branches**: Semantic version + branch name + run ID (e.g., `0.1.5-feature_branch-12345`)

## Usage

```yaml
- name: Generate Version
  id: version
  uses: ./.github/actions/generate-version

- name: Use generated version
  run: |
    echo "Version: ${{ steps.version.outputs.version }}"
    echo "Semantic Version: ${{ steps.version.outputs.semantic-version }}"
```

## Manual Version Updates

### Incrementing Major or Minor Version

To manually increment the major or minor version, follow these steps:

1. Update `VERSION` to the new `MAJOR.MINOR` line.
2. Commit that change.
3. The next build starts at patch `0`, and later builds increment automatically as more commits land.

### Best Practices

1. **Bump by editing `VERSION`**: Major and minor changes should happen only through that file.
2. **Commit the bump separately when possible**: That makes the patch reset point obvious in history.

### Version Output Examples

- **Main branch build**: `0.1.15`
- **PR #1234**: `0.1.15-PullRequest1234.8-17677917468`
- **Feature branch**: `0.1.15-feature_new_api-12345`
