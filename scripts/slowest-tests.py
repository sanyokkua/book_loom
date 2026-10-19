#!/usr/bin/env python3
"""Report where test time goes, from the JUnit XML reports Gradle already writes.

Reads modules/<module>/build/test-results/<task>/TEST-*.xml (default task: `test`) and prints:
  * per-module totals (classes, tests, failures, summed test time, and the wall-clock span of the run),
  * the slowest N test classes,
  * the slowest N test methods.

Summed time is the sum of the per-class times; with parallel forks the wall-clock span is the better measure of
how long the task took, so both are shown. Standard library only.

Usage:
    python3 scripts/slowest-tests.py                  # all modules, task `test`, top 30
    python3 scripts/slowest-tests.py ui pipeline      # only these modules
    python3 scripts/slowest-tests.py --task fastTest  # read another Test task's reports
    python3 scripts/slowest-tests.py --top 10
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


@dataclass(frozen=True)
class SuiteResult:
    module: str
    name: str
    tests: int
    failures: int
    seconds: float
    started: datetime | None


@dataclass(frozen=True)
class CaseResult:
    module: str
    suite: str
    name: str
    seconds: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("modules", nargs="*", help="module names (default: every module with reports)")
    parser.add_argument("--task", default="test", help="the Test task whose reports to read (default: test)")
    parser.add_argument("--top", type=int, default=30, help="how many classes and methods to list (default: 30)")
    return parser.parse_args()


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None


def read_reports(module: str, report_dir: Path) -> tuple[list[SuiteResult], list[CaseResult]]:
    suites: list[SuiteResult] = []
    cases: list[CaseResult] = []
    for path in sorted(report_dir.glob("TEST-*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            print(f"warning: cannot parse {path}: {error}", file=sys.stderr)
            continue
        name = root.get("name", path.stem)
        failures = int(root.get("failures", "0")) + int(root.get("errors", "0"))
        suites.append(
            SuiteResult(
                module=module,
                name=name,
                tests=int(root.get("tests", "0")),
                failures=failures,
                seconds=float(root.get("time", "0") or 0),
                started=parse_time(root.get("timestamp")),
            )
        )
        for case in root.iter("testcase"):
            cases.append(
                CaseResult(module, case.get("classname", name), case.get("name", "?"), float(case.get("time", "0") or 0))
            )
    return suites, cases


def wall_clock(suites: list[SuiteResult]) -> float:
    """The span from the first class start to the last class end, i.e. roughly how long the task ran."""
    spans = [(s.started, s.started + timedelta(seconds=s.seconds)) for s in suites if s.started]
    if not spans:
        return 0.0
    return (max(end for _, end in spans) - min(start for start, _ in spans)).total_seconds()


def short(name: str) -> str:
    return name.removeprefix("ua.bookloom.")


def print_modules(per_module: dict[str, list[SuiteResult]]) -> None:
    print(f"{'module':<12} {'classes':>7} {'tests':>6} {'failed':>6} {'summed s':>9} {'wall s':>8}")
    for module, suites in per_module.items():
        print(
            f"{module:<12} {len(suites):>7} {sum(s.tests for s in suites):>6} "
            f"{sum(s.failures for s in suites):>6} {sum(s.seconds for s in suites):>9.1f} {wall_clock(suites):>8.1f}"
        )


def main() -> int:
    args = parse_args()
    modules_dir = ROOT / "modules"
    wanted = args.modules or sorted(p.name for p in modules_dir.iterdir() if p.is_dir())
    per_module: dict[str, list[SuiteResult]] = {}
    all_cases: list[CaseResult] = []
    for module in wanted:
        report_dir = modules_dir / module / "build" / "test-results" / args.task
        if not report_dir.is_dir():
            continue
        suites, cases = read_reports(module, report_dir)
        if suites:
            per_module[module] = suites
            all_cases.extend(cases)
    if not per_module:
        print(f"no reports under modules/*/build/test-results/{args.task}", file=sys.stderr)
        return 1

    print_modules(per_module)

    all_suites = [s for suites in per_module.values() for s in suites]
    print(f"\nTop {args.top} classes by time")
    for suite in sorted(all_suites, key=lambda s: s.seconds, reverse=True)[: args.top]:
        print(f"{suite.seconds:>8.1f}s  {suite.tests:>4} tests  {suite.module:<11} {short(suite.name)}")

    print(f"\nTop {args.top} test methods by time")
    for case in sorted(all_cases, key=lambda c: c.seconds, reverse=True)[: args.top]:
        print(f"{case.seconds:>8.2f}s  {case.module:<11} {short(case.suite)}.{case.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
