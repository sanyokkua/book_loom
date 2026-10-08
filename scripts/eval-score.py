#!/usr/bin/env python3
"""Compare prompt-eval runs kept in eval-history/ (written by the promptEval tests, see docs/DEVELOPMENT.md).

  eval-score.py list [--history DIR]
  eval-score.py diff A B [--noise N] [--history DIR]
  eval-score.py selftest

A and B are run directories, globs, or selectors: `latest:<suite>[:<model>]` and `previous:<suite>[:<model>]`
(model is matched as a substring). Every number in the two reports is compared; a delta inside the noise band is
marked `~` (--noise N: N percentage points for rates, N percent of A for counts and costs; default 5, the sequence
eval's run-to-run noise is documented in docs/DEVELOPMENT.md#15e-after3). Stdlib only.
"""
import argparse
import glob
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROVENANCE_KEYS = ["gitSha", "gitDirty", "promptHash", "model", "label", "provider", "window", "dial", "brief", "suite"]
# Substrings of a metric's last path part; the first match decides. Lower is better for these.
LOWER = ("falsepositive", "falsenegative", "omission", "merge", "tooshort", "leaked", "callfailure", "failed", "repeated",
         "refused", "wasted", "calls", "flagged", "slip", "english", "quote", "mixedscript", "control", "residue",
         "vocative", "truncated", "hardgate", "variants", "renderingspert", "seconds", "elapsed", "tokens",
         "fallback", "attempts", "tokenbreaks", "claimed", "outputtokens", "percase", "perseg")
HIGHER = ("parse", "gate", "script", "marker", "injection", "separation", "stability", "idvalidity", "recall",
          "passrate", "rate", "dominantshare", "learnedcoverage", "bykind", "meets", "learned", "edits")
SKIP_LISTS = ("terms", "names", "learnedRenderings", "claimedRenderings")
RATE_WORDS = ("rate", "share", "parse", "gate", "script", "marker", "injection", "separation", "stability", "recall",
              "validity", "omission", "merge", "tooshort", "leaked", "callfailures", "falsepositive", "falsenegative",
              "coverage", "bykind", "falsepositive")


def history_dir(arg):
    return arg or os.environ.get("BOOKLOOM_EVAL_HISTORY_DIR") or os.path.join(ROOT, "eval-history")


def load(run):
    path = os.path.join(run, "report.json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def runs(history):
    """Run directories oldest first (the name starts with the timestamp)."""
    return sorted(d for d in glob.glob(os.path.join(history, "*")) if os.path.isfile(os.path.join(d, "report.json")))


def describe(run):
    p = load(run).get("provenance", {})
    return p


def resolve(selector, history):
    if os.path.isdir(selector) and os.path.isfile(os.path.join(selector, "report.json")):
        return selector
    if selector.split(":")[0] in ("latest", "previous"):
        kind, *rest = selector.split(":")
        suite = rest[0] if rest else ""
        model = rest[1] if len(rest) > 1 else ""
        hits = [r for r in runs(history)
                if (not suite or describe(r).get("suite") == suite) and model in describe(r).get("model", "")]
        index = 1 if kind == "latest" else 2
        if len(hits) < index:
            raise SystemExit("no run matches " + selector)
        return hits[-index]
    found = sorted(glob.glob(selector if os.path.isabs(selector) else os.path.join(history, selector)))
    found = [f for f in found if os.path.isfile(os.path.join(f, "report.json"))]
    if not found:
        raise SystemExit("no run matches " + selector)
    return found[-1]


def flatten(node, prefix=""):
    """Numeric leaves by dotted path; lists of objects with a key/size/id become path[key]."""
    out = {}
    if isinstance(node, bool):
        out[prefix] = float(node)
    elif isinstance(node, (int, float)):
        out[prefix] = float(node)
    elif isinstance(node, dict):
        for k, v in node.items():
            if k == "provenance" or (not prefix and k in SKIP_LISTS):
                continue
            out.update(flatten(v, prefix + "." + k if prefix else k))
    elif isinstance(node, list):
        for i, item in enumerate(node):
            id_key = next((k for k in ("key", "size", "id") if isinstance(item, dict) and k in item), None)
            label = str(item[id_key]) if id_key else str(i)
            body = {k: v for k, v in item.items() if k != id_key} if id_key else item
            out.update(flatten(body, prefix + "[" + label + "]"))
    return out


def direction(path):
    last = path.lower().split(".")[-1] if "[" not in path.split(".")[-1] else path.lower().split(".")[-1].split("[")[0]
    name = last
    for word in LOWER:
        if word in name:
            return -1
    for word in HIGHER:
        if word in name:
            return 1
    return 0


def is_rate(path, a, b):
    name = path.lower()
    return 0 <= a <= 1 and 0 <= b <= 1 and any(w in name for w in RATE_WORDS)


def compare(a, b, noise):
    """Rows of (path, a, b, delta, marker, note) for every metric present in both reports."""
    rows = []
    for path in sorted(set(a) & set(b)):
        va, vb = a[path], b[path]
        delta = vb - va
        if is_rate(path, va, vb):
            within, shown = abs(delta) * 100 <= noise, "%+.1f pts" % (delta * 100)
        else:
            within, shown = abs(delta) <= abs(va) * noise / 100, "%+.3g" % delta
        side = direction(path)
        if delta == 0:
            mark = "="
        elif within:
            mark = "~"
        elif side == 0:
            mark = "?"
        else:
            mark = "+" if delta * side > 0 else "-"
        rows.append((path, va, vb, shown, mark, {1: "higher is better", -1: "lower is better", 0: ""}[side]))
    return rows


def provenance_diff(pa, pb):
    lines = []
    for key in PROVENANCE_KEYS:
        if pa.get(key) != pb.get(key):
            lines.append("  %-11s %s -> %s" % (key, pa.get(key), pb.get(key)))
    return lines


def render(ra, rb, noise):
    out = []
    pa, pb = ra.get("provenance", {}), rb.get("provenance", {})
    out.append("suite %s (A %s, B %s)" % (pb.get("suite", pa.get("suite", "?")), pa.get("timestamp", "?"), pb.get("timestamp", "?")))
    changed = provenance_diff(pa, pb)
    out.append("what changed:" if changed else "what changed: nothing recorded (same sha, prompts, model, window)")
    out.extend(changed)
    rows = compare(flatten(ra), flatten(rb), noise)
    width = max([len(r[0]) for r in rows] + [6])
    out.append("%-*s %10s %10s %12s  %s" % (width, "metric", "A", "B", "delta", "(+ better, - worse, ~ within noise %g, = same)" % noise))
    for path, va, vb, shown, mark, note in rows:
        out.append("%-*s %10.4g %10.4g %12s  %s %s" % (width, path, va, vb, shown, mark, note))
    return "\n".join(out)


def cmd_list(args):
    for run in runs(history_dir(args.history)):
        p = describe(run)
        print("%-52s sha=%s%s prompts=%s window=%s %s" % (
            os.path.basename(run), p.get("gitSha", "?"), "*" if p.get("gitDirty") else "", p.get("promptHash", "?"),
            p.get("window", "?"), p.get("brief", "")))


def cmd_diff(args):
    history = history_dir(args.history)
    a, b = resolve(args.a, history), resolve(args.b, history)
    print("A: " + a + "\nB: " + b)
    print(render(load(a), load(b), args.noise))


def selftest():
    with tempfile.TemporaryDirectory() as tmp:
        for name, sha, parse, calls in (("20260101-000000-words-m", "aaa", 0.80, 10), ("20260102-000000-words-m", "bbb", 0.90, 12)):
            os.makedirs(os.path.join(tmp, name))
            report = {"provenance": {"suite": "words", "model": "m", "gitSha": sha, "timestamp": name[:8]},
                      "recall": parse, "calls": calls, "terms": [{"x": 1}], "cells": [{"size": 4, "omission": 0.1}]}
            with open(os.path.join(tmp, name, "report.json"), "w") as f:
                json.dump(report, f)
        a, b = resolve("previous:words:m", tmp), resolve("latest:words", tmp)
        assert a.endswith("000000-words-m") and "20260101" in a and "20260102" in b
        text = render(load(a), load(b), 5)
        assert "gitSha      aaa -> bbb" in text, text
        assert "recall" in text and "+10.0 pts" in text and " + " in text, text
        rows = {r[0]: r for r in compare(flatten(load(a)), flatten(load(b)), 5)}
        assert rows["calls"][4] == "-" and rows["cells[4].omission"][4] == "=", rows
        assert "terms" not in "".join(rows), rows
        assert compare({"recall": 0.80}, {"recall": 0.83}, 5)[0][4] == "~"
    print("selftest ok")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    lst = sub.add_parser("list")
    lst.add_argument("--history")
    diff = sub.add_parser("diff")
    diff.add_argument("a")
    diff.add_argument("b")
    diff.add_argument("--noise", type=float, default=5.0)
    diff.add_argument("--history")
    sub.add_parser("selftest")
    args = parser.parse_args()
    {"list": cmd_list, "diff": cmd_diff, "selftest": lambda _: selftest()}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
