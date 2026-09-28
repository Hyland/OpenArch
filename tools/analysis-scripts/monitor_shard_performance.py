#!/usr/bin/env python3
"""
GitHub Actions Shard Performance Monitor

Analyzes GitHub Actions run times to validate sharding effectiveness.
Use this after implementing sharded tests to measure performance gains.
"""

import json
import requests
import argparse
import re
from datetime import datetime, timedelta, timezone

# Constants
UTC_SUFFIX = '+00:00'

def normalize_github_timestamp(timestamp_str: str) -> datetime:
    """Convert GitHub API timestamp to timezone-aware datetime"""
    return datetime.fromisoformat(timestamp_str.replace('Z', UTC_SUFFIX))


def get_workflow_runs(repo_owner, repo_name, workflow_name, token, days_back=7):
    """Fetch recent workflow runs from GitHub API"""

    # Validate workflow_name to prevent API endpoint manipulation
    if not re.match(r'^[a-zA-Z0-9._-]+$', workflow_name):
        raise ValueError(f"Invalid workflow name: {workflow_name}. Only alphanumeric characters, dots, hyphens, and underscores are allowed.")

    headers = {
        'Authorization': f'token {token}',
        'Accept': 'application/vnd.github.v3+json'
    }

    # Calculate date range
    since = (datetime.now(timezone.utc) - timedelta(days=days_back)).isoformat().replace(UTC_SUFFIX, 'Z')

    url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/actions/workflows/{workflow_name}/runs"
    params = {
        'status': 'completed',
        'created': f'>{since}',
        'per_page': 50
    }

    response = requests.get(url, headers=headers, params=params)
    response.raise_for_status()

    return response.json()['workflow_runs']


def analyze_shard_performance(runs):
    """Analyze performance of sharded vs non-sharded runs"""

    sharded_runs = []
    non_sharded_runs = []

    for run in runs:
        # Look for shard indicators in the run logs/jobs
        if 'shard' in run['name'].lower() or 'Shard' in run['name']:
            sharded_runs.append(run)
        else:
            non_sharded_runs.append(run)

    def calculate_stats(run_list, name):
        if not run_list:
            return f"No {name} runs found"

        durations = []
        for run in run_list:
            if run['conclusion'] == 'success':
                start = normalize_github_timestamp(run['created_at'])
                end = normalize_github_timestamp(run['updated_at'])
                duration = (end - start).total_seconds()
                durations.append(duration)

        if not durations:
            return f"No successful {name} runs found"

        avg_duration = sum(durations) / len(durations)
        min_duration = min(durations)
        max_duration = max(durations)

        return {
            'count': len(durations),
            'avg_duration': avg_duration,
            'min_duration': min_duration,
            'max_duration': max_duration,
            'total_time_saved_per_run': None  # Will calculate later
        }

    sharded_stats = calculate_stats(sharded_runs, "sharded")
    non_sharded_stats = calculate_stats(non_sharded_runs, "non-sharded")

    return {
        'sharded': sharded_stats,
        'non_sharded': non_sharded_stats,
        'analysis_date': datetime.now(timezone.utc).isoformat().replace(UTC_SUFFIX, 'Z')
    }


def print_performance_report(stats):
    """Print a formatted performance report"""

    print("🚀 Shard Performance Analysis Report")
    print("=" * 50)
    print(f"Analysis Date: {stats['analysis_date']}")
    print()

    if isinstance(stats['sharded'], dict) and isinstance(stats['non_sharded'], dict):
        sharded = stats['sharded']
        non_sharded = stats['non_sharded']

        print("📊 Sharded Tests:")
        print(f"   - Runs analyzed: {sharded['count']}")
        print(f"   - Average duration: {sharded['avg_duration']:.1f}s ({sharded['avg_duration']/60:.1f}m)")
        print(f"   - Fastest run: {sharded['min_duration']:.1f}s")
        print(f"   - Slowest run: {sharded['max_duration']:.1f}s")
        print()

        print("📊 Non-Sharded Tests:")
        print(f"   - Runs analyzed: {non_sharded['count']}")
        print(f"   - Average duration: {non_sharded['avg_duration']:.1f}s ({non_sharded['avg_duration']/60:.1f}m)")
        print(f"   - Fastest run: {non_sharded['min_duration']:.1f}s")
        print(f"   - Slowest run: {non_sharded['max_duration']:.1f}s")
        print()

        # Calculate improvement
        if non_sharded['avg_duration'] > 0:
            improvement = ((non_sharded['avg_duration'] - sharded['avg_duration']) / non_sharded['avg_duration']) * 100
            time_saved = non_sharded['avg_duration'] - sharded['avg_duration']

            print("🎯 Performance Improvement:")
            print(f"   - Time saved per run: {time_saved:.1f}s ({time_saved/60:.1f}m)")
            print(f"   - Percentage improvement: {improvement:.1f}%")
            print(f"   - Weekly time savings (10 runs): {(time_saved * 10)/60:.1f}m")
    else:
        print("❌ Insufficient data for comparison")
        print(f"Sharded: {stats['sharded']}")
        print(f"Non-sharded: {stats['non_sharded']}")


def main():
    parser = argparse.ArgumentParser(description='Analyze shard performance from GitHub Actions')
    parser.add_argument('--repo', required=True, help='Repository in format owner/repo')
    parser.add_argument('--workflow', default='test-and-scan.yml', help='Workflow filename')
    parser.add_argument('--token', required=True, help='GitHub personal access token')
    parser.add_argument('--days', type=int, default=7, help='Days back to analyze')

    args = parser.parse_args()

    repo_owner, repo_name = args.repo.split('/')

    try:
        print(f"Fetching workflow runs for {args.repo}...")
        runs = get_workflow_runs(repo_owner, repo_name, args.workflow, args.token, args.days)

        print(f"Found {len(runs)} workflow runs in the last {args.days} days")

        stats = analyze_shard_performance(runs)
        print_performance_report(stats)

        # Save detailed results
        with open('shard_performance_analysis.json', 'w') as f:
            json.dump(stats, f, indent=2)

        print("\n📁 Detailed results saved to: shard_performance_analysis.json")

    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

    return 0


if __name__ == "__main__":
    exit(main())
