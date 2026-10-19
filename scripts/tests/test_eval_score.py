import importlib.machinery
import importlib.util
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


if __name__ == "__main__":
    unittest.main()
