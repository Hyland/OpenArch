#!/usr/bin/env python3
"""
Dynamic Integration Test Shard Generator

Analyzes test execution times and generates balanced shard configurations
for GitHub Actions workflows, combining all integration tests into 4 shards.
"""

import json
import argparse
import sys
import os
from pathlib import Path

# Ensure the script's directory is in sys.path for local imports
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir not in sys.path:
    sys.path.insert(0, script_dir)

from surefire_analysis import SurefireAnalyzer

def generate_balanced_integration_shards(analyzer, target_shards=4, max_time_per_shard=600):
    """Generate balanced shard configuration based on execution times"""

    # Get all integration test modules (includes legacy middleweight/heavyweight for compatibility)
    integration_modules = []
    for module_summary in analyzer.module_summaries.values():
        # Include modules with integration test suite marker (new unified approach)
        # Also include legacy middleweight/heavyweight markers for backward compatibility
        # This allows gradual migration from old markers to new .integration-test-suite
        if module_summary.test_suite_type in ["middleweight", "heavyweight", "integration"]:
            integration_modules.append({
                'name': module_summary.module_name,
                'path': module_summary.module_path,
                'time': module_summary.total_execution_time,
                'tests': len(module_summary.tests)
            })

    integration_modules.sort(key=lambda x: x['time'], reverse=True)

    # Bin packing algorithm for balanced shards
    shards = [[] for _ in range(target_shards)]
    shard_times = [0.0] * target_shards

    for module in integration_modules:
        # Find shard with minimum current time
        min_shard_idx = min(range(target_shards), key=lambda i: shard_times[i])

        # Check if adding this module exceeds max time
        if shard_times[min_shard_idx] + module['time'] <= max_time_per_shard:
            shards[min_shard_idx].append(module)
            shard_times[min_shard_idx] += module['time']
        else:
            # Create dedicated shard for large module
            shards.append([module])
            shard_times.append(module['time'])

    return shards, shard_times

# Shards 1-4 are reserved for common modules.
COMMON_SHARD_COUNT = 4

def generate_github_matrix(shards, root_path="."):
    """Generate GitHub Actions matrix configuration."""
    common_modules = [
        m for shard in shards for m in shard
    ]

    def _bin_pack(modules, num_shards):
        """Simple greedy bin-packing into num_shards buckets."""
        buckets = [[] for _ in range(num_shards)]
        times   = [0.0] * num_shards
        for mod in sorted(modules, key=lambda m: m['time'], reverse=True):
            idx = min(range(num_shards), key=lambda i: times[i])
            buckets[idx].append(mod)
            times[idx] += mod['time']
        return buckets

    common_buckets = _bin_pack(common_modules, COMMON_SHARD_COUNT)

    matrix_config = {"include": []}

    for i, bucket in enumerate(common_buckets, 1):
        if not bucket:
            continue
        shard_id    = f"shard-{i}"
        modules     = ",".join(m['name'] for m in bucket)
        total_time  = sum(m['time'] for m in bucket)
        total_tests = sum(m['tests'] for m in bucket)
        desc_names  = ", ".join(m['name'] for m in bucket)
        prefix      = "Common"

        matrix_config["include"].append({
            "shard": shard_id,
            "modules": modules,
            "expected_time": f"{total_time:.1f}s",
            "test_count": total_tests,
            "description": f"{prefix}: {desc_names}"
        })

    return matrix_config

def main():
    parser = argparse.ArgumentParser(description='Generate balanced integration test shards')
    parser.add_argument('--root-path', '-r', default='.',
                       help='Root path for analysis')
    parser.add_argument('--target-shards', '-s', type=int, default=4,
                       help='Target number of shards')
    parser.add_argument('--max-time', '-t', type=float, default=600.0,
                       help='Maximum time per shard in seconds')
    parser.add_argument('--output', '-o', default='integration-shards.json',
                       help='Output file for shard configuration')

    args = parser.parse_args()

    # Analyze current test reports
    analyzer = SurefireAnalyzer(args.root_path)
    analyzer.analyze()

    # Collect all integration modules (flat list — generate_github_matrix handles the split)
    shards, _ = generate_balanced_integration_shards(
        analyzer, COMMON_SHARD_COUNT, args.max_time
    )

    # Generate GitHub matrix configuration (balanced shards, up to COMMON_SHARD_COUNT)
    matrix_config = generate_github_matrix(shards, args.root_path)

    # Output results
    print("Generated Integration Test Shard Configuration:")
    print("=" * 60)
    for entry in matrix_config["include"]:
        print(f"{entry['shard']} ({entry['expected_time']}): {entry['description']}")
    print()

    # Save to JSON
    with open(args.output, 'w') as f:
        json.dump(matrix_config, f, indent=2)

    print(f"GitHub Actions matrix configuration saved to: {args.output}")
    print("Use in your workflow with:")
    print("Common strategy:\n  matrix: ${{ fromJson(needs.setup-test-matrices.outputs.integration_common_matrix) }}")

if __name__ == "__main__":
    main()
