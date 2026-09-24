import importlib.util
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "sync-agent-files.py"
SPEC = importlib.util.spec_from_file_location("sync_agent_files", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
SYNC_AGENT_FILES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SYNC_AGENT_FILES)


class RenderTomlTest(unittest.TestCase):
    def test_omits_claude_tool_allowlist_from_codex_agent(self):
        rendered = SYNC_AGENT_FILES.render_toml(
            "investigator",
            {"name": "investigator", "tools": "Read, Grep, Glob, Bash"},
            "Read-only investigation instructions.",
        )

        self.assertNotRegex(rendered, r"(?m)^tools\s*=")


if __name__ == "__main__":
    unittest.main()
