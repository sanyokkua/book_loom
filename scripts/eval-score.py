#!/usr/bin/env python3
"""Compare prompt-eval runs kept in .temporary_context/eval-history/ (written by the promptEval tests, see docs/DEVELOPMENT.md).

  eval-score.py list [--history DIR]
  eval-score.py diff A B [--noise N] [--history DIR]
  eval-score.py diff --paired A B [--history DIR]
  eval-score.py calibrate --items cal-1/items.json --labels cal-1/labels.json --judge RUN [--history DIR]
  eval-score.py show RUN CASE [--history DIR]
  eval-score.py blind A B --out pairs.md --key key.json --seed N [--all-repeats] [--only IDS|@FILE] [--history DIR]
  eval-score.py baseline RUN --out eval/baseline/<suite>-<pair>-<model>-<dial>.json [--history DIR]
  eval-score.py selftest

`baseline` copies one run into a committed baseline file: the provenance (with `pipelineCommit` and `baselineOf`), the
run-level aggregates and noise band, every repeat's case record (the run's cases.jsonl, else the report's cases), the
outputs and the rest of the report. It refuses a run that is not version 2, whose dataset is missing or has
`ownerData` other than false, or that has no `baselineOf`.

A and B are run directories, globs, or selectors: `latest:<suite>[:<model>]` and `previous:<suite>[:<model>]`
(model is matched as a substring). Every number in the two reports is compared; a delta inside the noise band is
marked `~` (--noise N: N percentage points for rates, N percent of A for counts and costs; default 5, the sequence
eval's run-to-run noise is documented in docs/DEVELOPMENT.md#15e-after3). Stdlib only.

The statistics of the redesign (design D24) live here and nowhere else. Version-2 reports (provenance.version == 2)
carry a top-level `cases` array of per-repeat records {caseId, repeat, seed, graders: [{id, level, passed}], time,
tokens, replyRef, judge?}; the calls file replyRef names (relative to the run folder) holds [{prompt, reply}, ...].
`diff --paired` averages a case's repeats first, then counts per binary grader the cases only B passes more often
against those only A passes more often (discordant, "b:a") and gives the exact two-sided sign test; a judge score
(`judge.score`, or the mean of `judge.scores`) gets the paired mean difference and a 95 % bootstrap interval (2000
resamples, fixed seed). With K >= 2 the noise band is the range of the repeats' aggregates (the pass rate over all
graders, the mean judge score) and is used instead of --noise. Screen rule: some grader moves >= 2 cases to B and no
grader loses 2 or more. Real-effect rule: a grader with p < 0.05, or a judge interval that excludes 0 with a
difference above the noise band. Two runs of different report versions are refused.

`calibrate` reads items.json {"items": [{id, kind: single|pair, split: few-shot|dev|test}]}, labels.json
{"graders": {<grader id>: {id: label}}} with exactly two graders, the developer agent and the second reader (a single item's label has `meaningError` and `score`, a pair's
`winner` A|B|tie) and the judge run's cases, whose `judge` holds `meaningError`, `score`, or for a pair `winner` and
`winnerSwapped` (the verdict with the order swapped, in the original A/B names; a win counts only when both agree, else
tie). The reference label is the label both graders gave; items they disagree on or one of them skipped are counted
("left out") and not scored. Only the test split is scored.
"""
import argparse
import glob
import json
import math
import os
import random
import re
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASETS_DIR = os.path.join(ROOT, "modules", "pipeline", "src", "test", "resources", "eval", "datasets")
NO_CASES = "no per-case graders; use plain diff"
PROVENANCE_KEYS = ["gitSha", "gitDirty", "promptHash", "model", "label", "provider", "window", "dial", "brief", "suite"]
# Substrings of a metric's last path part; the first match decides. Lower is better for these.
LOWER = ("falsepositive", "falsenegative", "omission", "merge", "tooshort", "leaked", "callfailure", "failed", "repeated",
         "refused", "wasted", "calls", "flagged", "slip", "english", "quote", "mixedscript", "control", "residue",
         "vocative", "truncated", "hardgate", "variants", "renderingspert", "seconds", "elapsed", "tokens",
         "fallback", "attempts", "tokenbreaks", "claimed", "outputtokens", "percase", "perseg")
HIGHER = ("parse", "gate", "script", "marker", "injection", "stability", "idvalidity", "recall",
          "passrate", "rate", "dominantshare", "learnedcoverage", "bykind", "meets", "learned", "edits")
SKIP_LISTS = ("terms", "names", "learnedRenderings", "claimedRenderings")
RATE_WORDS = ("rate", "share", "parse", "gate", "script", "marker", "injection", "separation", "stability", "recall",
              "validity", "omission", "merge", "tooshort", "leaked", "callfailures", "falsepositive", "falsenegative",
              "coverage", "bykind", "falsepositive")


def history_dir(arg):
    return arg or os.environ.get("BOOKLOOM_EVAL_HISTORY_DIR") or os.path.join(ROOT, ".temporary_context", "eval-history")


def load(run):
    path = run if os.path.isfile(run) else os.path.join(run, "report.json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def runs(history):
    """Run directories oldest first (the name starts with the timestamp)."""
    return sorted(d for d in glob.glob(os.path.join(history, "*")) if os.path.isfile(os.path.join(d, "report.json")))


def describe(run):
    p = load(run).get("provenance", {})
    return p


def resolve(selector, history):
    if os.path.isfile(selector) and selector.endswith(".json"):
        return selector
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
    if args.paired:
        return cmd_paired(args)
    history = history_dir(args.history)
    a, b = resolve(args.a, history), resolve(args.b, history)
    print("A: " + a + "\nB: " + b)
    print(render(load(a), load(b), args.noise))


Z95 = 1.96
BOOTSTRAP_RESAMPLES = 2000
BOOTSTRAP_SEED = 20261028
REPORT_VERSION = 2
SIGNIFICANT = 0.05


def sign_test(wins, losses):
    """Exact two-sided sign test on the discordant counts; 1.0 when there are none."""
    n = wins + losses
    if n == 0:
        return 1.0
    tail = sum(math.comb(n, k) for k in range(min(wins, losses) + 1))
    return min(1.0, 2 * tail / 2 ** n)


def noise_band(aggregates):
    """The range of the repeats' aggregate scores, or None below two repeats."""
    if len(aggregates) < 2:
        return None
    return max(aggregates) - min(aggregates)


def wilson_lower(successes, total, z=Z95):
    """Lower bound of the Wilson score interval (0 for no items)."""
    if total == 0:
        return 0.0
    p = successes / total
    centre = p + z * z / (2 * total)
    margin = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total))
    return (centre - margin) / (1 + z * z / total)


def kendall_tau_b(xs, ys):
    """Kendall's tau-b of two equally long lists; None when either has no variation."""
    concordant = discordant = ties_x = ties_y = 0
    for i in range(len(xs)):
        for j in range(i + 1, len(xs)):
            dx, dy = xs[i] - xs[j], ys[i] - ys[j]
            if dx == 0 and dy == 0:
                continue
            if dx == 0:
                ties_x += 1
            elif dy == 0:
                ties_y += 1
            elif (dx > 0) == (dy > 0):
                concordant += 1
            else:
                discordant += 1
    denominator = math.sqrt((concordant + discordant + ties_x) * (concordant + discordant + ties_y))
    return None if denominator == 0 else (concordant - discordant) / denominator


def bootstrap_interval(diffs):
    """95 % percentile interval of the mean of paired differences, 2000 resamples, fixed seed."""
    rng = random.Random(BOOTSTRAP_SEED)
    means = sorted(sum(rng.choices(diffs, k=len(diffs))) / len(diffs) for _ in range(BOOTSTRAP_RESAMPLES))
    return means[int(0.025 * BOOTSTRAP_RESAMPLES)], means[int(0.975 * BOOTSTRAP_RESAMPLES) - 1]


def version_of(report):
    return report.get("provenance", {}).get("version", 1)


def load_v2(run):
    report = load(run)
    if version_of(report) != REPORT_VERSION:
        raise SystemExit("%s is a version-%s report; version %d is needed" % (run, version_of(report), REPORT_VERSION))
    return report


def judge_score(record):
    judge = record.get("judge")
    if not isinstance(judge, dict):
        return None
    if isinstance(judge.get("score"), (int, float)):
        return float(judge["score"])
    scores = [v for v in (judge.get("scores") or {}).values() if isinstance(v, (int, float))]
    return sum(scores) / len(scores) if scores else None


def per_case(report):
    """{caseId: ({grader: mean pass}, mean judge score or None)}, each case's repeats averaged."""
    grouped = {}
    for record in report.get("cases", []):
        grouped.setdefault(record["caseId"], []).append(record)
    out = {}
    for case_id, records in grouped.items():
        graders = {}
        for record in records:
            for g in record.get("graders", []):
                graders.setdefault(g["id"], []).append(1.0 if g["passed"] else 0.0)
        scores = [s for s in (judge_score(r) for r in records) if s is not None]
        out[case_id] = ({k: sum(v) / len(v) for k, v in graders.items()},
                        sum(scores) / len(scores) if scores else None)
    return out


def repeat_aggregates(report):
    """Per repeat: the pass rate over every grader result, and the mean judge score (None when no judge)."""
    passes, scores = {}, {}
    for record in report.get("cases", []):
        r = record.get("repeat", 0)
        passes.setdefault(r, []).extend(1.0 if g["passed"] else 0.0 for g in record.get("graders", []))
        score = judge_score(record)
        if score is not None:
            scores.setdefault(r, []).append(score)
    mean = lambda values: sum(values) / len(values)
    return [mean(v) for v in passes.values() if v], [mean(v) for v in scores.values()]


def grader_band(report):
    """Noise band of the grader pass rate: what the Java side recorded in provenance, else the repeats' cases."""
    provenance = report.get("provenance", {})
    if provenance.get("noiseBand") is not None:
        return provenance["noiseBand"]
    band = noise_band(provenance.get("aggregates") or [])
    if band is not None:
        return band
    return noise_band(repeat_aggregates(report)[0])


DATASET_KEYS = ["id", "rootHash", "labelsHash"]


def dataset_diff(pa, pb):
    da, db = pa.get("dataset") or {}, pb.get("dataset") or {}
    return ["  dataset.%-9s %s -> %s" % (k, da.get(k), db.get(k)) for k in DATASET_KEYS if da.get(k) != db.get(k)]


def paired_report(ra, rb):
    """The lines of `diff --paired` for two version-2 reports."""
    a, b = per_case(ra), per_case(rb)
    shared = sorted(set(a) & set(b))
    out = ["paired on %d cases (A only %d, B only %d)" % (len(shared), len(set(a) - set(b)), len(set(b) - set(a)))]
    provenance_lines = (provenance_diff(ra.get("provenance", {}), rb.get("provenance", {}))
                        + dataset_diff(ra.get("provenance", {}), rb.get("provenance", {})))
    out.append("what changed:" if provenance_lines else "what changed: nothing recorded")
    out.extend(provenance_lines)
    counts, significant, moved, lost = {}, False, False, False
    for grader in sorted({g for c in shared for g in a[c][0].keys() & b[c][0].keys()}):
        wins = sum(1 for c in shared if grader in a[c][0] and grader in b[c][0] and b[c][0][grader] > a[c][0][grader])
        losses = sum(1 for c in shared if grader in a[c][0] and grader in b[c][0] and b[c][0][grader] < a[c][0][grader])
        p = sign_test(wins, losses)
        counts[grader] = (wins, losses, p)
        significant |= p < SIGNIFICANT
        moved |= wins - losses >= 2
        lost |= losses - wins >= 2
        out.append("grader %-28s discordant %d:%d  p=%.5f%s" % (grader, wins, losses, p, "  *" if p < SIGNIFICANT else ""))
    band = max([x for x in (grader_band(ra), grader_band(rb)) if x is not None], default=None)
    judge_band = max([x for x in (noise_band(repeat_aggregates(ra)[1]), noise_band(repeat_aggregates(rb)[1])) if x is not None],
                     default=None)
    interval_wins, mean = False, 0.0
    diffs = [b[c][1] - a[c][1] for c in shared if a[c][1] is not None and b[c][1] is not None]
    if diffs:
        mean = sum(diffs) / len(diffs)
        low, high = bootstrap_interval(diffs)
        interval_wins = (low > 0 or high < 0) and abs(mean) > (judge_band or 0.0)
        out.append("judge score mean difference (B - A) %+.3f, 95%% interval [%+.3f, %+.3f], %d cases" % (mean, low, high, len(diffs)))
    out.append("noise band: " + ("none (K=1)" if band is None else "grader pass rate %.4f" % band)
               + ("" if judge_band is None else ", judge score %.4f" % judge_band))
    out.append("screen: %s (moves >= 2 cases: %s; a metric loses 2: %s)" % (
        "pass" if moved and not lost else "fail", "yes" if moved else "no", "yes" if lost else "no"))
    out.append("real effect: %s (p < 0.05: %s; interval excludes 0 beyond the noise band: %s)" % (
        effect_text(significant or interval_wins, lost, moved, mean), "yes" if significant else "no", "yes" if interval_wins else "no"))
    return out


def effect_text(real, lost, moved, judge_mean):
    """'no', or 'yes, B better' / 'yes, B worse' from the discordant counts (the judge mean breaks a tie)."""
    if not real:
        return "no"
    if lost != moved:
        return "yes, B worse" if lost else "yes, B better"
    return "yes, B worse" if judge_mean < 0 else "yes, B better"


def cmd_paired(args):
    history = history_dir(args.history)
    a, b = resolve(args.a, history), resolve(args.b, history)
    ra, rb = load(a), load(b)
    ra["cases"], rb["cases"] = run_cases(a, ra), run_cases(b, rb)
    if not ra["cases"] and not rb["cases"]:
        print("A: " + a + "\nB: " + b + "\n" + NO_CASES)
        return
    if version_of(ra) != version_of(rb):
        raise SystemExit("cannot compare a version-%s report with a version-%s report; rerun the older suite" % (
            version_of(ra), version_of(rb)))
    if version_of(ra) != REPORT_VERSION:
        raise SystemExit("--paired needs version-%d reports, got version %s" % (REPORT_VERSION, version_of(ra)))
    id_a, id_b = ((r.get("provenance", {}).get("dataset") or {}).get("id") for r in (ra, rb))
    if id_a != id_b:
        raise SystemExit("cannot compare runs of different datasets (A %s, B %s)" % (id_a, id_b))
    print("A: " + a + "\nB: " + b)
    print("\n".join(paired_report(ra, rb)))


def ratio_line(name, successes, total):
    rate = "n/a" if total == 0 else "%.3f" % (successes / total)
    return "%s %s (%d/%d) wilson-lower %.3f" % (name, rate, successes, total, wilson_lower(successes, total))


def reference_labels(labels):
    """({id: label every grader gave}, number of ids left out); the file holds the two graders' labels."""
    graders = list(labels["graders"].values())
    if len(graders) != 2:
        raise SystemExit("labels.json must hold exactly two graders, found %d" % len(graders))
    first, second = graders
    agreed, left_out = {}, 0
    for item_id in sorted(set(first) | set(second)):
        merged = agreed_label(first.get(item_id), second.get(item_id))
        if merged is None:
            left_out += 1
        else:
            agreed[item_id] = merged
    return agreed, left_out


def agreed_label(one, two):
    """The label both graders stand behind, or None. A single item's scores agree when within 1 (the mean is kept)."""
    if one is None or two is None:
        return None
    if "meaningError" not in one:
        return one if one == two else None
    if one["meaningError"] != two["meaningError"] or abs(one["score"] - two["score"]) > 1:
        return None
    return {"meaningError": one["meaningError"], "score": (one["score"] + two["score"]) / 2}


def effective_winner(judge):
    first, second = judge.get("winner"), judge.get("winnerSwapped")
    return first if first == second else "tie"


def calibration_lines(items, labels, judge_report, split="test"):
    reference, left_out = reference_labels(labels)
    judged = {}
    for record in judge_report.get("cases", []):
        judged.setdefault(record["caseId"], record.get("judge") or {})
    test = [i for i in items["items"] if i.get("split") == split]
    singles = [i["id"] for i in test if i.get("kind") == "single" and i["id"] in reference and i["id"] in judged]
    pairs = [i["id"] for i in test if i.get("kind") == "pair" and i["id"] in reference and i["id"] in judged]
    positives = [i for i in singles if reference[i].get("meaningError")]
    negatives = [i for i in singles if not reference[i].get("meaningError")]
    scored = [i for i in singles if isinstance(judged[i].get("score"), (int, float)) and "score" in reference[i]]
    consistent = sum(1 for i in pairs if judged[i].get("winner") == judged[i].get("winnerSwapped"))
    correct = sum(1 for i in pairs if effective_winner(judged[i]) == reference[i].get("winner"))
    tau = kendall_tau_b([reference[i]["score"] for i in scored], [judged[i]["score"] for i in scored])
    return [
        "%s split: %d single, %d pair items scored" % (split, len(singles), len(pairs)),
        ratio_line("TPR", sum(1 for i in positives if judged[i].get("meaningError")), len(positives)),
        ratio_line("TNR", sum(1 for i in negatives if not judged[i].get("meaningError")), len(negatives)),
        ratio_line("position consistency", consistent, len(pairs)).rsplit(" wilson", 1)[0],
        "Kendall tau-b " + ("n/a" if tau is None else "%.3f" % tau) + " over %d scored items" % len(scored),
        ratio_line("pairwise accuracy", correct, len(pairs)).rsplit(" wilson", 1)[0],
        "left out %d (graders disagree or one is missing)" % left_out,
    ]


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def cmd_calibrate(args):
    judge_run = resolve(args.judge, history_dir(args.history))
    print("\n".join(calibration_lines(read_json(args.items), read_json(args.labels), load_v2(judge_run), args.split)))


def calls_of(run, record):
    ref = record.get("replyRef") or "calls/%s-%d.json" % (re.sub(r"[^A-Za-z0-9._-]", "_", record["caseId"]), record.get("repeat", 0))
    path = os.path.join(run, ref)
    if not os.path.isfile(path):
        return []
    return read_json(path)


def dataset_sources(report):
    """{block id: text} of the report's dataset in the repo's eval datasets, empty when it is not a book dataset."""
    dataset_id = (report.get("provenance", {}).get("dataset") or {}).get("id")
    path = os.path.join(DATASETS_DIR, dataset_id or "", "book.json")
    if not dataset_id or not os.path.isfile(path):
        return {}
    return {b["id"]: b["text"] for b in read_json(path).get("blocks", []) if "id" in b and "text" in b}


def source_of(record, sources):
    return record.get("source") or sources.get(record["caseId"])


def cmd_show(args):
    run = resolve(args.run, history_dir(args.history))
    report = load(run)
    records = [r for r in run_cases(run, report) if r["caseId"] == args.case]
    if not records:
        raise SystemExit("no case %s in %s" % (args.case, run))
    source = source_of(records[0], dataset_sources(report))
    if source:
        print("source: " + source)
    target = report.get("outputs", {}).get(args.case)
    if target is not None:
        print("target (repeat 0): " + target)
    for record in records:
        print("== case %s repeat %s seed %s" % (record["caseId"], record.get("repeat"), record.get("seed")))
        for g in record.get("graders", []):
            print("grader %-28s %-5s %s" % (g["id"], g.get("level", ""), "pass" if g["passed"] else "FAIL"))
        print("time %s tokens %s" % (record.get("time"), record.get("tokens")))
        print("judge: %s" % json.dumps(record.get("judge")))
        for n, call in enumerate(calls_of(run, record), 1):
            print("-- call %d prompt:\n%s\n-- call %d reply:\n%s" % (n, call.get("prompt"), n, call.get("reply")))


def final_output(run, report, record):
    target = report.get("outputs", {}).get(record["caseId"])
    if target is not None:
        return target
    calls = calls_of(run, record)
    return calls[-1].get("reply") if calls else None


def cmd_blind(args):
    history = history_dir(args.history)
    a, b = resolve(args.a, history), resolve(args.b, history)
    report_a, report_b = load(a), load(b)
    only = wanted_cases(args.only)
    if args.all_repeats:
        first, second = repeat_outputs(a, report_a), repeat_outputs(b, report_b)
    else:
        first = {r["caseId"]: r for r in run_cases(a, report_a) if r.get("repeat", 0) == 0}
        second = {r["caseId"]: r for r in run_cases(b, report_b) if r.get("repeat", 0) == 0}
    if not first and not second:
        print(NO_CASES)
        return
    sources = dataset_sources(report_a)
    rng = random.Random(args.seed)
    key, lines = {}, ["# Blind pairs", ""]
    for case_id in sorted(set(first) & set(second)):
        if only is not None and case_id not in only:
            continue
        if args.all_repeats:
            (source, out_a), (_, out_b) = first[case_id], second[case_id]
        else:
            source = source_of(first[case_id], sources)
            out_a, out_b = final_output(a, report_a, first[case_id]), final_output(b, report_b, second[case_id])
        if out_a is None or out_b is None or out_a == out_b:
            continue
        flip = rng.random() < 0.5
        shown = (("B", out_b), ("A", out_a)) if flip else (("A", out_a), ("B", out_b))
        key[case_id] = {"X": shown[0][0], "Y": shown[1][0]}
        lines.append("## " + case_id)
        if source:
            lines += ["Source:", "", source, ""]
        for label, (_, out) in zip(("X", "Y"), shown):
            lines += ["Option %s:" % label, ""] + (repeat_lines(out) if args.all_repeats else [out]) + [""]
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    with open(args.key, "w", encoding="utf-8") as f:
        json.dump({"seed": args.seed, "a": a, "b": b, "pairs": key}, f, indent=2, ensure_ascii=False)
    print("%d changed cases written to %s, key in %s" % (len(key), args.out, args.key))


def wanted_cases(only):
    """The case ids --only names (comma-separated, or @file with one id per line), or None for every case."""
    if not only:
        return None
    if only.startswith("@"):
        with open(only[1:], encoding="utf-8") as f:
            return {line.strip() for line in f if line.strip()}
    return {part.strip() for part in only.split(",") if part.strip()}


def repeat_outputs(run, report):
    """{case id: (source, [output of repeat 0, 1, ...])} from records that keep their own `output` (an arm's run)."""
    by_case = {}
    for record in run_cases(run, report):
        if "output" in record:
            source, outputs = by_case.setdefault(record["caseId"], (record.get("source"), {}))
            outputs[record.get("repeat", 0)] = record["output"]
    return {case_id: (source, [outputs[k] for k in sorted(outputs)]) for case_id, (source, outputs) in by_case.items()}


def repeat_lines(outputs):
    return ["repeat %d: %s" % (n + 1, out) for n, out in enumerate(outputs)]


def refuse_baseline(provenance):
    """The reason a run may not become a baseline, or None."""
    if provenance.get("version") != REPORT_VERSION:
        return "the run is a version-%s report; a baseline needs version %d" % (provenance.get("version", 1), REPORT_VERSION)
    dataset = provenance.get("dataset")
    if not isinstance(dataset, dict) or dataset.get("ownerData") is not False:
        return "the run's dataset is missing or holds owner data; only invented datasets are committed"
    if not provenance.get("baselineOf"):
        return "the run has no provenance.baselineOf; run it with --baseline-of <commit>"
    return None


def run_cases(run, report):
    """Every repeat's case records: the run folder's cases.jsonl, else the report's own (repeat 0) cases."""
    path = os.path.join(run, "cases.jsonl")
    if os.path.isfile(run) or not os.path.isfile(path):
        return report.get("cases", [])
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def cmd_baseline(args):
    run = resolve(args.run, history_dir(args.history))
    report = load(run)
    provenance = report.get("provenance", {})
    reason = refuse_baseline(provenance)
    if reason:
        raise SystemExit("%s: %s" % (run, reason))
    cases = run_cases(run, report)
    aggregates = provenance.get("aggregates") or repeat_aggregates({"cases": cases})[0]
    band = provenance.get("noiseBand")
    if band is None:
        band = noise_band(aggregates)
    rest = {k: v for k, v in report.items() if k not in ("provenance", "cases", "outputs")}
    baseline = {"provenance": provenance, "aggregates": aggregates, "noiseBand": band, "cases": cases,
                "outputs": report.get("outputs", {}), "report": rest}
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(baseline, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print("baseline of %s written to %s (%d cases, %d repeats)" % (provenance["baselineOf"], args.out, len(cases), len(aggregates)))


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
    assert sign_test(6, 0) == 0.03125 and sign_test(5, 0) == 0.0625 and sign_test(15, 4) < 0.05
    assert abs(noise_band([0.7, 0.85, 0.8]) - 0.15) < 1e-9 and noise_band([1]) is None
    assert abs(wilson_lower(9, 10) - 0.5958) < 1e-3
    print("selftest ok")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    lst = sub.add_parser("list")
    lst.add_argument("--history")
    diff = sub.add_parser("diff")
    diff.add_argument("a")
    diff.add_argument("b")
    diff.add_argument("--noise", type=float, default=5.0)
    diff.add_argument("--paired", action="store_true")
    diff.add_argument("--history")
    cal = sub.add_parser("calibrate")
    cal.add_argument("--items", required=True)
    cal.add_argument("--labels", required=True)
    cal.add_argument("--judge", required=True)
    cal.add_argument("--split", default="test", help="split to score; only tuning rounds use dev")
    cal.add_argument("--history")
    show = sub.add_parser("show")
    show.add_argument("run")
    show.add_argument("case")
    show.add_argument("--history")
    blind = sub.add_parser("blind")
    blind.add_argument("a")
    blind.add_argument("b")
    blind.add_argument("--out", required=True)
    blind.add_argument("--key", required=True)
    blind.add_argument("--seed", type=int, required=True)
    blind.add_argument("--all-repeats", action="store_true",
                       help="show every repeat's output (records that keep `output`, an arm's run), not repeat 0 only")
    blind.add_argument("--only", help="case ids to show: comma-separated, or @file with one id per line")
    blind.add_argument("--history")
    base = sub.add_parser("baseline")
    base.add_argument("run")
    base.add_argument("--out", required=True)
    base.add_argument("--history")
    sub.add_parser("selftest")
    args = parser.parse_args(argv)
    {"list": cmd_list, "diff": cmd_diff, "calibrate": cmd_calibrate, "show": cmd_show, "blind": cmd_blind, "baseline": cmd_baseline,
     "selftest": lambda _: selftest()}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
