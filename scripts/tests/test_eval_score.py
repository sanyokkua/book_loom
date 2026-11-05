import importlib.machinery
import contextlib
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "eval-score.py"
LOADER = importlib.machinery.SourceFileLoader("eval_score", str(SCRIPT_PATH))
SPEC = importlib.util.spec_from_loader("eval_score", LOADER)
assert SPEC is not None
SCORE = importlib.util.module_from_spec(SPEC)
LOADER.exec_module(SCORE)


class EvalScoreTest(unittest.TestCase):
    def test_selftest_runsTheBuiltInScenario(self):
        SCORE.selftest()

    def test_compare_changeInsideNoise_isMarkedTilde(self):
        self.assertEqual("~", SCORE.compare({"recall": 0.80}, {"recall": 0.83}, 5)[0][4])

    def test_compare_lowerIsBetterMetricThatRises_isMarkedMinus(self):
        self.assertEqual("-", SCORE.compare({"falsePositive": 0.1}, {"falsePositive": 0.3}, 5)[0][4])


def write_run(root, name, version, cases):
    """A run folder whose report.json holds the given cases (caseId, repeat, graders, optional judge/reply)."""
    run = Path(root) / name
    run.mkdir()
    provenance = {"suite": "mini", "model": "m"}
    if version:
        provenance["version"] = version
    (run / "report.json").write_text(json.dumps({"provenance": provenance, "cases": cases}), encoding="utf-8")
    return str(run)


def case(case_id, passed, repeat=0, judge=None):
    record = {"caseId": case_id, "repeat": repeat, "graders": [{"id": "G", "level": "HARD", "passed": passed}]}
    if judge is not None:
        record["judge"] = judge
    return record


def run_main(argv):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        SCORE.main(argv)
    return out.getvalue()



def baseline_provenance(**overrides):
    provenance = {"version": 2, "suite": "mini", "model": {"id": "m"}, "dial": "BALANCED",
                  "pipelineCommit": "0123456789abcdef0123456789abcdef01234567", "baselineOf": "79ffede6",
                  "dataset": {"id": "mini-en", "ownerData": False}, "aggregates": [0.8, 0.9, 0.85],
                  "noiseBand": 0.1, "repeats": {"k": 3, "seeds": [1000, 1001, 1002]}}
    provenance.update(overrides)
    return provenance


def write_baseline_run(root, provenance, cases=None, name="20261028-000000-mini-m"):
    run = Path(root) / name
    run.mkdir()
    report = {"provenance": provenance, "passRate": 0.85, "cases": [case("b1", True)],
              "outputs": {"b1": "text"}}
    (run / "report.json").write_text(json.dumps(report), encoding="utf-8")
    if cases is not None:
        (run / "cases.jsonl").write_text("\n".join(json.dumps(c) for c in cases) + "\n", encoding="utf-8")
    return str(run)


class BaselineTest(unittest.TestCase):
    def baseline(self, provenance, cases=None):
        tmp = tempfile.mkdtemp()
        run = write_baseline_run(tmp, provenance, cases)
        out = str(Path(tmp) / "eval" / "baseline" / "mini-en-uk-m-balanced.json")
        SCORE.main(["baseline", run, "--out", out])
        return json.loads(Path(out).read_text(encoding="utf-8"))

    def test_baseline_v2Run_keepsProvenanceAggregatesNoiseBandCasesAndOutputs(self):
        written = self.baseline(baseline_provenance(), [case("b1", True, repeat=0), case("b1", False, repeat=1)])

        self.assertEqual("79ffede6", written["provenance"]["baselineOf"])
        self.assertEqual("0123456789abcdef0123456789abcdef01234567", written["provenance"]["pipelineCommit"])
        self.assertEqual([0.8, 0.9, 0.85], written["aggregates"])
        self.assertEqual(0.1, written["noiseBand"])
        self.assertEqual([0, 1], [c["repeat"] for c in written["cases"]])
        self.assertEqual({"b1": "text"}, written["outputs"])
        self.assertEqual(0.85, written["report"]["passRate"])

    def test_baseline_withoutCasesFile_fallsBackToTheReportsCases(self):
        written = self.baseline(baseline_provenance())

        self.assertEqual(["b1"], [c["caseId"] for c in written["cases"]])

    def test_baseline_withoutRecordedAggregates_computesThemFromTheCases(self):
        provenance = baseline_provenance()
        del provenance["aggregates"], provenance["noiseBand"]
        written = self.baseline(provenance, [case("b1", True, repeat=0), case("b1", False, repeat=1)])

        self.assertEqual([1.0, 0.0], written["aggregates"])
        self.assertEqual(1.0, written["noiseBand"])

    def test_baseline_refusedRun_writesNothing(self):
        refused = {
            "version 1": baseline_provenance(version=1),
            "owner data": baseline_provenance(dataset={"id": "x", "ownerData": True}),
            "no dataset": baseline_provenance(dataset=None),
            "no baselineOf": baseline_provenance(baselineOf=None),
        }
        for label, provenance in refused.items():
            with self.subTest(label), tempfile.TemporaryDirectory() as tmp:
                run = write_baseline_run(tmp, provenance)
                out = Path(tmp) / "out.json"
                with self.assertRaises(SystemExit):
                    SCORE.main(["baseline", run, "--out", str(out)])
                self.assertFalse(out.exists())


class SignTestTest(unittest.TestCase):
    def test_signTest_sixToZero_isBelowFivePercent(self):
        self.assertEqual(0.03125, SCORE.sign_test(6, 0))

    def test_signTest_fiveToZero_isNot(self):
        self.assertEqual(0.0625, SCORE.sign_test(5, 0))

    def test_signTest_fifteenToFour_isBelowFivePercent(self):
        self.assertLess(SCORE.sign_test(15, 4), 0.05)
        self.assertAlmostEqual(0.0192, SCORE.sign_test(15, 4), places=4)

    def test_signTest_noDiscordantCases_isOne(self):
        self.assertEqual(1.0, SCORE.sign_test(0, 0))


class NoiseBandTest(unittest.TestCase):
    def test_noiseBand_threeRepeats_isTheRangeOfTheAggregates(self):
        self.assertAlmostEqual(0.15, SCORE.noise_band([0.70, 0.85, 0.80]))

    def test_noiseBand_oneRepeat_isNone(self):
        self.assertIsNone(SCORE.noise_band([0.7]))


class PairedDiffTest(unittest.TestCase):
    def test_diff_versionOneAgainstTwo_isRefused(self):
        with tempfile.TemporaryDirectory() as tmp:
            old = write_run(tmp, "old", None, [case("a", True)])
            new = write_run(tmp, "new", 2, [case("a", True)])
            with self.assertRaises(SystemExit) as raised:
                run_main(["diff", "--paired", old, new])
            self.assertIn("version", str(raised.exception))

    def test_diff_pairedSixToZero_printsPAndTheRealEffect(self):
        with tempfile.TemporaryDirectory() as tmp:
            ids = ["c%d" % i for i in range(6)]
            a = write_run(tmp, "a", 2, [case(i, False) for i in ids])
            b = write_run(tmp, "b", 2, [case(i, True) for i in ids])
            text = run_main(["diff", "--paired", a, b])
            self.assertIn("6:0", text)
            self.assertIn("p=0.03125", text)
            self.assertIn("real effect: yes", text)
            self.assertIn("screen: pass", text)

    def test_diff_paired_printsEachSidesPromptCostWithoutWeighingIt(self):
        with tempfile.TemporaryDirectory() as tmp:
            costed = case("x", True)
            costed.update({"time": {"promptRead": 2000}, "tokens": {"prompt": 300, "cached": 120}})
            a = write_run(tmp, "a", 2, [case("x", True)])
            b = write_run(tmp, "b", 2, [costed])
            text = run_main(["diff", "--paired", a, b])
            self.assertIn("cost A: promptRead 0.0 s, prompt tokens 0, cached 0", text)
            self.assertIn("cost B: promptRead 2.0 s, prompt tokens 300, cached 120", text)

    def test_diff_repeatsAreAveragedBeforePairing(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = write_run(tmp, "a", 2, [case("x", True, 0), case("x", False, 1), case("y", False, 0), case("y", False, 1)])
            b = write_run(tmp, "b", 2, [case("x", True, 0), case("x", True, 1), case("y", False, 0), case("y", False, 1)])
            text = run_main(["diff", "--paired", a, b])
            self.assertIn("1:0", text)


def real_run(tmp, name, passes_by_repeat, k_in_jsonl=True):
    """A run in the real shape: report.json holds repeat 0 only, cases.jsonl holds every repeat."""
    run = Path(tmp) / name
    run.mkdir()
    records = [case("c%d" % i, passed, repeat) for repeat, passes in enumerate(passes_by_repeat)
               for i, passed in enumerate(passes)]
    first = [r for r in records if r["repeat"] == 0]
    (run / "report.json").write_text(json.dumps({"provenance": {"version": 2, "suite": "mini", "model": "m"},
                                                 "cases": first}), encoding="utf-8")
    if k_in_jsonl:
        (run / "cases.jsonl").write_text("\n".join(json.dumps(r) for r in records), encoding="utf-8")
    return str(run)


def baseline_file(tmp, name, passes_by_repeat, noise_band=None):
    records = [case("c%d" % i, passed, repeat) for repeat, passes in enumerate(passes_by_repeat)
               for i, passed in enumerate(passes)]
    provenance = {"version": 2, "suite": "mini", "model": {"id": "m"}, "noiseBand": noise_band}
    outputs = {"c%d" % i: "target %d" % i for i in range(len(passes_by_repeat[0]))}
    path = Path(tmp) / name
    path.write_text(json.dumps({"provenance": provenance, "cases": records, "outputs": outputs}), encoding="utf-8")
    return str(path)


class RealShapeTest(unittest.TestCase):
    def test_diff_paired_kThreeRunInRealShape_printsANoiseBand(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = real_run(tmp, "a", [[True, False], [True, True], [False, False]])
            b = real_run(tmp, "b", [[True, True], [True, True], [True, False]])
            text = run_main(["diff", "--paired", a, b])
            self.assertNotIn("none (K=1)", text)
            self.assertIn("grader pass rate", text)

    def test_diff_paired_baselineFiles_areAcceptedAndUseAllRepeats(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = baseline_file(tmp, "a.json", [[False, False], [False, False], [False, False]])
            b = baseline_file(tmp, "b.json", [[True, True], [True, True], [True, True]])
            text = run_main(["diff", "--paired", a, b])
            self.assertIn("2:0", text)
            self.assertIn("grader pass rate", text)

    def test_diff_paired_noPerRepeatData_fallsBackToProvenanceNoiseBand(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = baseline_file(tmp, "a.json", [[True]], noise_band=0.0625)
            b = baseline_file(tmp, "b.json", [[True]], noise_band=0.0625)
            self.assertIn("grader pass rate 0.0625", run_main(["diff", "--paired", a, b]))

    def test_diff_paired_significantLoss_saysBWorse(self):
        with tempfile.TemporaryDirectory() as tmp:
            ids = ["c%d" % i for i in range(6)]
            a = write_run(tmp, "a", 2, [case(i, True) for i in ids])
            b = write_run(tmp, "b", 2, [case(i, False) for i in ids])
            text = run_main(["diff", "--paired", a, b])
            self.assertIn("real effect: yes, B worse", text)
            self.assertIn("real effect: yes, B better", run_main(["diff", "--paired", b, a]))

    def test_show_baselineFile_printsSourceTargetAndGraders(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = baseline_file(tmp, "a.json", [[True], [False]])
            text = run_main(["show", path, "c0"])
            self.assertIn("target 0", text)
            self.assertIn("grader G", text)
            self.assertEqual(2, text.count("== case c0"))

    def test_blind_baselineFiles_useDecidedTargets(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = baseline_file(tmp, "a.json", [[True, True]])
            b = Path(tmp) / "b.json"
            data = json.loads(Path(a).read_text())
            data["outputs"] = {k: v + " changed" for k, v in data["outputs"].items()}
            b.write_text(json.dumps(data), encoding="utf-8")
            out, key = str(Path(tmp) / "p.md"), str(Path(tmp) / "k.json")
            run_main(["blind", a, str(b), "--out", out, "--key", key, "--seed", "3"])
            text = Path(out).read_text()
            self.assertIn("target 0", text)
            self.assertIn("target 0 changed", text)
            self.assertEqual(2, len(json.loads(Path(key).read_text())["pairs"]))


def items_and_labels(tmp):
    """Twenty invented test items plus one dev item: 10 defective and 5 clean singles, 5 pairs."""
    items, agent, codex, judge = [], {}, {}, []
    for n in range(15):
        item_id = "s%d" % n
        defective = n < 10
        items.append({"id": item_id, "kind": "single", "split": "test"})
        label = {"meaningError": defective, "score": 1 if defective else 5}
        agent[item_id], codex[item_id] = dict(label), dict(label)
        flagged = defective if n != 9 else False
        judge.append({"caseId": item_id, "repeat": 0, "judge": {"meaningError": flagged, "score": 1 if flagged else 5}})
    for n in range(5):
        item_id = "p%d" % n
        items.append({"id": item_id, "kind": "pair", "split": "test"})
        agent[item_id] = codex[item_id] = {"winner": "A"}
        verdict = {"winner": "A", "winnerSwapped": "A" if n != 4 else "B"}
        if n == 3:
            verdict = {"winner": "B", "winnerSwapped": "B"}
        judge.append({"caseId": item_id, "repeat": 0, "judge": verdict})
    items.append({"id": "dev1", "kind": "single", "split": "dev"})
    agent["dev1"] = codex["dev1"] = {"meaningError": True, "score": 0}
    judge.append({"caseId": "dev1", "repeat": 0, "judge": {"meaningError": False, "score": 5}})
    paths = {}
    for name, data in (("items", {"items": items}), ("labels", {"graders": {"agent": agent, "codex": codex}})):
        paths[name] = str(Path(tmp) / (name + ".json"))
        Path(paths[name]).write_text(json.dumps(data), encoding="utf-8")
    paths["judge"] = write_run(tmp, "judge", 2, judge)
    return paths, codex


class CalibrateTest(unittest.TestCase):
    def test_calibrate_inventedLabels_printsWilsonLowerBounds(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths, _ = items_and_labels(tmp)
            text = run_main(["calibrate", "--items", paths["items"], "--labels", paths["labels"], "--judge", paths["judge"]])
            self.assertIn("TPR 0.900 (9/10) wilson-lower 0.596", text)
            self.assertIn("TNR 1.000 (5/5) wilson-lower 0.566", text)
            self.assertIn("position consistency 0.800 (4/5)", text)
            self.assertIn("pairwise accuracy 0.600 (3/5)", text)
            self.assertIn("left out 0", text)

    def test_calibrate_disagreeingReferenceItems_areCountedNotScored(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths, codex = items_and_labels(tmp)
            codex["s0"] = {"meaningError": False, "score": 5}
            codex["p0"] = {"winner": "B"}
            labels = json.loads(Path(paths["labels"]).read_text())
            labels["graders"]["codex"] = codex
            Path(paths["labels"]).write_text(json.dumps(labels))
            text = run_main(["calibrate", "--items", paths["items"], "--labels", paths["labels"], "--judge", paths["judge"]])
            self.assertIn("TPR 0.889 (8/9)", text)
            self.assertIn("pairwise accuracy 0.500 (2/4)", text)
            self.assertIn("left out 2", text)

    def test_kendallTauB_oneSwappedPair_isTwoThirds(self):
        self.assertAlmostEqual(0.6667, SCORE.kendall_tau_b([1, 2, 3, 4], [1, 3, 2, 4]), places=4)

    def test_wilsonLower_nineOfTen_is0596(self):
        self.assertAlmostEqual(0.5958, SCORE.wilson_lower(9, 10), places=4)


class BlindTest(unittest.TestCase):
    def test_blind_allRepeats_showsEveryRepeatOfTheOnlyCasesAndHidesTheArm(self):
        with tempfile.TemporaryDirectory() as tmp:
            def arm(name, word):
                cases = [dict(case(c, True, k), source="src " + c, output="%s %s %d" % (word, c, k))
                         for c in ("c0", "c1") for k in range(3)]
                return write_run(tmp, name, 2, cases)
            a, b = arm("a", "json"), arm("b", "markers")
            out, key = str(Path(tmp) / "p.md"), str(Path(tmp) / "k.json")
            run_main(["blind", a, b, "--out", out, "--key", key, "--seed", "5", "--all-repeats", "--only", "c1"])
            text = Path(out).read_text()
            self.assertIn("src c1", text)
            self.assertIn("repeat 3: json c1 2", text)
            self.assertIn("repeat 3: markers c1 2", text)
            self.assertNotIn("c0", text)
            self.assertNotIn("Option A", text)
            self.assertEqual(["c1"], list(json.loads(Path(key).read_text())["pairs"]))

    def test_blind_sameSeed_givesTheSameShuffleAndAKeyFile(self):
        with tempfile.TemporaryDirectory() as tmp:
            a_cases = [case("c%d" % i, True) for i in range(8)]
            a = write_run(tmp, "a", 2, a_cases)
            b = write_run(tmp, "b", 2, a_cases)
            for run, tag in ((a, "A"), (b, "B")):
                (Path(run) / "calls").mkdir()
                for i in range(8):
                    ref = "calls/c%d-0.json" % i
                    (Path(run) / ref).write_text(json.dumps([{"prompt": "src %d" % i, "reply": "%s %d" % (tag, i)}]))

            def blind(seed, suffix):
                out, key = str(Path(tmp) / ("pairs%s.md" % suffix)), str(Path(tmp) / ("key%s.json" % suffix))
                run_main(["blind", a, b, "--out", out, "--key", key, "--seed", str(seed)])
                return Path(out).read_text(), json.loads(Path(key).read_text())
            first, key1 = blind(7, "1")
            second, key2 = blind(7, "2")
            self.assertEqual(first, second)
            self.assertEqual(key1, key2)
            self.assertEqual(8, len(key1["pairs"]))
            self.assertTrue(all(set(p.values()) == {"A", "B"} for p in key1["pairs"].values()))
            self.assertNotIn("Option A", first)


    def test_agreedLabel_singleScoresWithinOne_areKeptAsTheirMean(self):
        a = {"meaningError": False, "score": 4}
        self.assertEqual({"meaningError": False, "score": 4.5}, SCORE.agreed_label(a, {"meaningError": False, "score": 5}))
        self.assertIsNone(SCORE.agreed_label(a, {"meaningError": False, "score": 2}))
        self.assertIsNone(SCORE.agreed_label(a, {"meaningError": True, "score": 4}))
        self.assertEqual({"winner": "A"}, SCORE.agreed_label({"winner": "A"}, {"winner": "A"}))
        self.assertIsNone(SCORE.agreed_label({"winner": "A"}, {"winner": "tie"}))


if __name__ == "__main__":
    unittest.main()


def dataset_run(tmp, name, dataset, cases, outputs=None, **provenance):
    base = {"version": 2, "suite": "mini", "model": {"id": "m"}, "dataset": dataset}
    base.update(provenance)
    path = Path(tmp) / name
    report = {"provenance": base, "cases": cases, "outputs": outputs or {}}
    path.write_text(json.dumps(report), encoding="utf-8")
    return str(path)


class SourceLookupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        folder = Path(self.tmp.name) / "datasets" / "mini-x"
        folder.mkdir(parents=True)
        (folder / "book.json").write_text(json.dumps({"blocks": [{"id": "c0", "text": "Source of c0"}]}), encoding="utf-8")
        self.old = SCORE.DATASETS_DIR
        SCORE.DATASETS_DIR = str(Path(self.tmp.name) / "datasets")
        self.addCleanup(setattr, SCORE, "DATASETS_DIR", self.old)

    def test_show_caseWithoutSource_readsItFromTheDatasetOnceWithTargetLabel(self):
        path = dataset_run(self.tmp.name, "a.json", {"id": "mini-x"}, [case("c0", True, 0), case("c0", True, 1)], {"c0": "T"})
        text = run_main(["show", path, "c0"])
        self.assertEqual(1, text.count("source: Source of c0"))
        self.assertEqual(1, text.count("target (repeat 0): T"))
        self.assertEqual(2, text.count("== case c0"))

    def test_blind_caseWithoutSource_includesTheDatasetSource(self):
        a = dataset_run(self.tmp.name, "a.json", {"id": "mini-x"}, [case("c0", True)], {"c0": "one"})
        b = dataset_run(self.tmp.name, "b.json", {"id": "mini-x"}, [case("c0", True)], {"c0": "two"})
        out, key = str(Path(self.tmp.name) / "p.md"), str(Path(self.tmp.name) / "k.json")
        run_main(["blind", a, b, "--out", out, "--key", key, "--seed", "1"])
        self.assertIn("Source of c0", Path(out).read_text())

    def test_blind_runsWithoutCases_saysUsePlainDiff(self):
        a = dataset_run(self.tmp.name, "a.json", {"id": "mini-x"}, [])
        out, key = str(Path(self.tmp.name) / "p.md"), str(Path(self.tmp.name) / "k.json")
        text = run_main(["blind", a, a, "--out", out, "--key", key, "--seed", "1"])
        self.assertIn("no per-case graders; use plain diff", text)


class NoiseBandProvenanceTest(unittest.TestCase):
    def test_graderBand_provenanceNoiseBand_winsOverCases(self):
        report = {"provenance": {"noiseBand": 0.0385}, "cases": [case("c0", True, 0), case("c0", False, 1)]}
        self.assertEqual(0.0385, SCORE.grader_band(report))

    def test_graderBand_provenanceAggregates_winOverCases(self):
        report = {"provenance": {"aggregates": [0.9, 0.8]}, "cases": [case("c0", True, 0), case("c0", False, 1)]}
        self.assertAlmostEqual(0.1, SCORE.grader_band(report))

    def test_graderBand_realBaselines_printRecordedBands(self):
        base = Path(SCORE.ROOT) / "modules/pipeline/src/test/resources/eval/baseline"
        for name, band in (("mini-ru-uk-gemma4_e4b-mlx-max.json", "0.0385"), ("mini-en-uk-gemma4_e4b-mlx-balanced.json", "0.0175")):
            path = str(base / name)
            self.assertIn("grader pass rate " + band, run_main(["diff", "--paired", path, path]))


class DatasetCompareTest(unittest.TestCase):
    def test_diff_paired_differentDatasetId_isRefused(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = dataset_run(tmp, "a.json", {"id": "d1"}, [case("c0", True)])
            b = dataset_run(tmp, "b.json", {"id": "d2"}, [case("c0", True)])
            with self.assertRaises(SystemExit) as raised:
                run_main(["diff", "--paired", a, b])
            self.assertIn("d1", str(raised.exception))

    def test_diff_paired_changedHashes_areListedUnderWhatChanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = dataset_run(tmp, "a.json", {"id": "d1", "rootHash": "r1", "labelsHash": "l1"}, [case("c0", True)])
            b = dataset_run(tmp, "b.json", {"id": "d1", "rootHash": "r2", "labelsHash": "l2"}, [case("c0", True)])
            text = run_main(["diff", "--paired", a, b])
            self.assertIn("rootHash", text)
            self.assertIn("r1 -> r2", text)
            self.assertIn("l1 -> l2", text)

    def test_diff_paired_runsWithoutCases_saysUsePlainDiff(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = dataset_run(tmp, "a.json", {"id": "d1"}, [])
            self.assertIn("no per-case graders; use plain diff", run_main(["diff", "--paired", a, a]))
