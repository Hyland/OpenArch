#!/usr/bin/env python3
"""
Surefire Test Report Analyzer

This script analyzes Maven Surefire test reports and generates a markdown report
showing test execution times grouped by test suite type (lightweight, integration).
"""

import os
import datetime
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import defaultdict
from typing import Dict, List, Tuple, Optional
import argparse


class TestResult:
    """Represents a single test result"""
    def __init__(self, test_class: str, test_name: str, execution_time: float, module_path: str):
        self.test_class = test_class
        self.test_name = test_name
        self.execution_time = execution_time
        self.module_path = module_path


class ModuleSummary:
    """Represents a summary of tests for a single module"""
    def __init__(self, module_name: str, module_path: str, test_suite_type: str):
        self.module_name = module_name
        self.module_path = module_path
        self.test_suite_type = test_suite_type
        self.tests: List[TestResult] = []
        self.total_execution_time = 0.0

    def add_test(self, test: TestResult):
        self.tests.append(test)
        self.total_execution_time += test.execution_time

    def get_sorted_tests(self) -> List[TestResult]:
        """Return tests sorted by execution time (descending)"""
        return sorted(self.tests, key=lambda x: x.execution_time, reverse=True)


class SurefireAnalyzer:
    """Main analyzer class"""

    def __init__(self, root_path: str):
        self.root_path = Path(root_path)
        self.module_summaries: Dict[str, ModuleSummary] = {}

    def find_test_suite_type(self, module_path: Path) -> str:
        """Determine the test suite type for a module based on marker files"""
        if (module_path / ".lightweight-test-suite").exists():
            return "lightweight"
        elif (module_path / ".integration-test-suite").exists():
            return "integration"
        elif (module_path / ".middleweight-test-suite").exists():
            return "middleweight"
        elif (module_path / ".heavyweight-test-suite").exists():
            return "heavyweight"
        else:
            return "unknown"

    def find_surefire_reports(self) -> List[Path]:
        """Find all surefire test report XML files"""
        reports = []
        for xml_file in self.root_path.rglob("target/surefire-reports/TEST-*.xml"):
            reports.append(xml_file)
        return reports

    def parse_test_report(self, xml_file: Path) -> List[TestResult]:
        """Parse a single surefire test report XML file"""
        tests = []
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()

            # Extract module path (go up from target/surefire-reports/TEST-*.xml to module root)
            module_path = xml_file.parent.parent.parent

            # Parse test cases
            for testcase in root.findall('testcase'):
                test_class = testcase.get('classname', '')
                test_name = testcase.get('name', '')
                execution_time = float(testcase.get('time', '0'))

                test = TestResult(test_class, test_name, execution_time, str(module_path))
                tests.append(test)

        except ET.ParseError as e:
            print(f"Error parsing {xml_file}: {e}")
        except Exception as e:
            print(f"Unexpected error parsing {xml_file}: {e}")

        return tests

    def analyze(self):
        """Main analysis method"""
        print("Finding surefire test reports...")
        report_files = self.find_surefire_reports()
        print(f"Found {len(report_files)} test report files")

        # Group tests by module
        for report_file in report_files:
            tests = self.parse_test_report(report_file)
            if tests:
                # Get module path and name
                module_path = Path(tests[0].module_path)
                module_name = module_path.name

                # Determine test suite type
                test_suite_type = self.find_test_suite_type(module_path)

                # Create or get module summary
                module_key = str(module_path)
                if module_key not in self.module_summaries:
                    self.module_summaries[module_key] = ModuleSummary(
                        module_name, str(module_path), test_suite_type
                    )

                # Add tests to module summary
                for test in tests:
                    self.module_summaries[module_key].add_test(test)

        print(f"Analyzed {len(self.module_summaries)} modules")

    def generate_markdown_report(self, output_file: str = "test_analysis_report.md"):
        """Generate markdown report"""
        # Group modules by test suite type
        suite_groups = defaultdict(list)
        for module_summary in self.module_summaries.values():
            suite_groups[module_summary.test_suite_type].append(module_summary)

        # Sort modules within each group by total execution time (descending)
        for suite_type in suite_groups:
            suite_groups[suite_type].sort(key=lambda x: x.total_execution_time, reverse=True)

        # Generate markdown content
        markdown_content = []
        markdown_content.append("# Surefire Test Analysis Report")
        markdown_content.append("")
        markdown_content.append(f"Generated on: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        markdown_content.append("")

        # Summary section
        markdown_content.append("## Summary")
        markdown_content.append("")
        total_modules = len(self.module_summaries)
        total_tests = sum(len(ms.tests) for ms in self.module_summaries.values())
        total_time = sum(ms.total_execution_time for ms in self.module_summaries.values())

        markdown_content.append(f"- **Total Modules**: {total_modules}")
        markdown_content.append(f"- **Total Tests**: {total_tests}")
        markdown_content.append(f"- **Total Execution Time**: {total_time:.3f}s")
        markdown_content.append("")

        # Test suite distribution
        markdown_content.append("### Test Suite Distribution")
        markdown_content.append("")
        markdown_content.append("| Test Suite Type | Module Count | Total Tests | Total Time (s) |")
        markdown_content.append("|----------------|--------------|-------------|----------------|")

        for suite_type in sorted(suite_groups.keys()):
            modules = suite_groups[suite_type]
            module_count = len(modules)
            test_count = sum(len(ms.tests) for ms in modules)
            suite_time = sum(ms.total_execution_time for ms in modules)
            markdown_content.append(f"| {suite_type.title()} | {module_count} | {test_count} | {suite_time:.3f} |")

        markdown_content.append("")

        # Detailed sections for each test suite type
        for suite_type in ["lightweight", "middleweight", "heavyweight", "unknown"]:
            if suite_type not in suite_groups:
                continue

            modules = suite_groups[suite_type]
            if not modules:
                continue

            markdown_content.append(f"## {suite_type.title()} Test Suite")
            markdown_content.append("")
            markdown_content.append(f"**Modules sorted by total execution time (longest first)**")
            markdown_content.append("")

            for i, module_summary in enumerate(modules, 1):
                markdown_content.append(f"### {i}. {module_summary.module_name}")
                markdown_content.append("")
                markdown_content.append(f"**Module Path**: `{module_summary.module_path}`")
                markdown_content.append(f"**Total Execution Time**: {module_summary.total_execution_time:.3f}s")
                markdown_content.append(f"**Number of Tests**: {len(module_summary.tests)}")
                markdown_content.append("")

                if module_summary.tests:
                    markdown_content.append("| Test Class | Test Name | Execution Time (s) |")
                    markdown_content.append("|------------|-----------|-------------------|")

                    # Show tests sorted by execution time
                    sorted_tests = module_summary.get_sorted_tests()
                    for test in sorted_tests:
                        # Truncate long class names for better readability
                        short_class = test.test_class.split('.')[-1] if '.' in test.test_class else test.test_class
                        markdown_content.append(f"| `{short_class}` | `{test.test_name}` | {test.execution_time:.3f} |")

                    markdown_content.append("")
                else:
                    markdown_content.append("*No tests found*")
                    markdown_content.append("")

        # Write to file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(markdown_content))

        print(f"Report written to: {output_file}")


def main():
    parser = argparse.ArgumentParser(description='Analyze Maven Surefire test reports')
    parser.add_argument('--root-path', '-r',
                       default='.',
                       help='Root path to search for surefire reports (default: current directory)')
    parser.add_argument('--output', '-o',
                       default='test_analysis_report.md',
                       help='Output markdown file name (default: test_analysis_report.md)')

    args = parser.parse_args()

    # Validate root path
    if not os.path.exists(args.root_path):
        print(f"Error: Root path '{args.root_path}' does not exist")
        return 1

    try:
        analyzer = SurefireAnalyzer(args.root_path)
        analyzer.analyze()
        analyzer.generate_markdown_report(args.output)
        print("Analysis completed successfully!")
        return 0
    except Exception as e:
        print(f"Error during analysis: {e}")
        return 1


if __name__ == "__main__":
    exit(main())
