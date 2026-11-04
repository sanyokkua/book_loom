#!/usr/bin/env python3
"""Compare the bundled model-knowledge CSVs with the research data they were generated from.

Usage:
    python3 -I scripts/model-knowledge-check.py <research-data-dir>

<research-data-dir> is the folder holding quant-types.csv and declared-languages.csv (the research task
11-quants-and-names/data), or the research root that contains that folder. The bundled files are
modules/llm/src/main/resources/ua/bookloom/llm/knowledge/quant-tiers.csv and declared-languages.csv; this script
reproduces the research-to-bundle mapping their header comments document, then reports every row that differs:
changed, only in the research data, or only in the bundle. A bundled row that a person changed on purpose is left
out when a '# deliberate:' header line names it (a token or a model name, matched loosely).

Exits 0 with no row output when the two agree; exits 1 after printing each differing row; exits 0 after printing
"research data not found" when the folder holds no research CSVs; exits 2 on a usage or mapping error. Stale knowledge
is found by a person running this, never by the app. Standard library only; no network.
"""

from __future__ import annotations

import argparse
import csv
import io
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BUNDLE = REPO / "modules/llm/src/main/resources/ua/bookloom/llm/knowledge"
QUANT_FILE = "quant-types.csv"
LANGUAGE_FILE = "declared-languages.csv"
QUANT_BUNDLE = "quant-tiers.csv"
LANGUAGE_BUNDLE = "declared-languages.csv"
RESEARCH_SUBDIR = Path("11-quants-and-names/data")
QUANT_SOURCE = "research 11-quants-and-names 2026-10-28"
CAUTION_SOURCE = "; Q3 on a large dense model is acceptable with a caution (one source)"
CAUTIOUS_QUANTS = ("Q3_K_L", "Q3_K_M", "Q3_K_XL")
QUANT_COLUMNS = ["token", "family", "bpw", "needsImatrix", "small", "mid", "large", "moe", "confidence", "source"]
LANGUAGE_COLUMNS = ["key", "matchKind", "pattern", "listKind", "codes", "source", "evidence"]
DELIBERATE_PREFIX = "# deliberate:"
MAX_TERM_WORDS = 4
IMATRIX_VALUES = ("yes", "recommended", "no", "n/a")
TIER = {
    "excellent": "EXCELLENT", "good": "GOOD", "acceptable": "ACCEPTABLE", "poor": "POOR",
    "unusable": "UNUSABLE", "unmeasured": "UNMEASURED", "removed": "REMOVED", "n/a": "",
}
FAMILY = {
    "float": "float", "legacy": "legacy", "legacy (removed)": "legacy", "k": "k", "k+bartowski": "k",
    "i": "i", "ternary": "ternary", "mx": "mx",
}
# (research family, generation prefix) -> bundled pattern keys; the first entry whose prefix matches wins.
GENERATION_PATTERNS = {
    ("Gemma", "TranslateGemma"): ["translategemma"],
    ("Mistral", "Small 3.1"): ["mistral-small-3.1"], ("Mistral", "Small 3 ("): ["mistral-small-3"],
    ("Mistral", "Magistral"): ["magistral-small"], ("Mistral", "Devstral Small 2507"): ["devstral-small-2507"],
    ("Mistral", "Devstral Small 2 "): ["devstral-small-2"], ("Mistral", "Small 4"): ["mistral-small-4"],
    ("Mistral", "Ministral 3"): ["ministral-3", "mistral-large-3"], ("Mistral", "Nemo"): ["mistral-nemo"],
    ("Mistral", "Mixtral"): ["mixtral", "mistral-7b"],
    ("Phi", "3.5-mini"): ["phi-3.5-mini"], ("Phi", "4-mini"): ["phi-4-mini", "phi-4-multimodal"],
    ("Phi", "4 ("): ["phi-4"],
    ("DeepSeek", "V3"): ["deepseek-v3", "deepseek-r1"], ("DeepSeek", "R1 distills"): ["deepseek-r1-distill"],
    ("GLM", "4"): ["glm-4", "glm4"], ("Granite", "3.0"): ["granite"],
    ("Nemotron", "Nano"): ["nemotron-nano", "nemotron-3-nano"], ("Nemotron", "3 Super"): ["nemotron-3-super"],
    ("Nemotron", "nemotron3"): ["nemotron3", "nemotron-3.5-lightning"],
    ("gpt-oss", "20b"): ["gpt-oss"],
    ("Cohere", "Command R /"): ["command-r"], ("Cohere", "Aya 23"): ["aya", "command-r7b", "command-a"],
    ("Cohere", "Tiny Aya"): ["tiny-aya"], ("Cohere", "Command A Vision"): ["command-a-vision"],
    ("EuroLLM", "1.7B"): ["eurollm"], ("Salamandra", "2B"): ["salamandra", "alia"], ("Teuken", "7B"): ["teuken"],
    ("OLMo", "2 / 3"): ["olmo"], ("SmolLM", "3"): ["smollm3"], ("Falcon", "3"): ["falcon3"],
    ("Falcon", "H1"): ["falcon-h1"], ("Hermes", "4"): ["hermes-4"], ("MiniCPM", "4.1"): ["minicpm"],
    ("InternLM", "2.5"): ["internlm", "intern-s1"], ("Kimi", "K2"): ["kimi"], ("Seed-X", "Instruct"): ["seed-x"],
    ("Hunyuan-MT", "7B"): ["hunyuan-mt"], ("Tower", "Tower+"): ["tower-plus"],
    ("Tower", "TowerInstruct"): ["towerinstruct"],
    ("MADLAD-400 / NLLB-200", "google"): ["madlad400", "nllb-200"],
    ("MamayLM (INSAIT)", "v0.1"): ["mamaylm"], ("Lapa", "v0.1.2"): ["lapa"],
    ("Qwen", "3-2507"): ["qwen3-2507"], ("Qwen", "3-VL"): ["qwen3-vl"], ("Qwen", "3 ("): ["qwen3"],
    ("Qwen", "3.5"): ["qwen3.5"], ("Qwen", "3.6"): ["qwen3.6"], ("Qwen", "3.8"): ["qwen3.8"],
    ("Qwen", "2.5"): ["qwen2.5"],
    ("Gemma", "2 ("): ["gemma2"], ("Gemma", "3 ("): ["gemma3"], ("Gemma", "3n"): ["gemma3n"],
    ("Gemma", "4"): ["gemma4"],
}
# Rows the research folder does not hold: they are written by hand in the bundle, so they are added here too.
HAND_LANGUAGE_ROWS = [
    ["muse-glimmer", "EXACT", "muse-glimmer", "NONE", "", "hand row", "no statement read"],
    ["nemotron-3.5-bolt", "EXACT", "nemotron-3.5-bolt", "NONE", "", "hand row", "no statement read"],
    ["ministral-3-reasoning", "PREFIX", "ministral-3-reasoning", "NONE", "", "hand row", "card list not read"],
]


class MappingError(Exception):
    """A research value the mapping does not know; reported on stderr with exit 2, never guessed."""


def lookup(table: dict[str, str], cell: str) -> str:
    if cell not in table:
        raise MappingError(f"unknown research value {cell!r}")
    return table[cell]


def read_dicts(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def quant_tokens(research_type: str) -> list[str] | None:
    if research_type.startswith(("Q4_K_XL /", "MXFP4_MOE (converted")):
        return None
    if research_type.startswith("MXFP4_MOE (gpt-oss native"):
        return ["MXFP4"]
    if research_type.startswith("Q4_0_4_4"):
        return ["Q4_0_4_4", "Q4_0_4_8", "Q4_0_8_8"]
    return [research_type]


def first_word(cell: str) -> str:
    words = cell.split()
    return words[0].lower() if words else "n/a"


def quant_row(research: dict[str, str], token: str) -> list[str]:
    bpw = re.match(r"~?([0-9.]+)", research["bpw"])
    if bpw is None:
        raise MappingError(f"no bits-per-weight figure in {research['bpw']!r} for {research['type']}")
    imatrix = first_word(research["needs_imatrix"])
    if imatrix not in IMATRIX_VALUES:
        raise MappingError(f"unknown needs_imatrix {imatrix!r} for {research['type']}")
    source = QUANT_SOURCE + (CAUTION_SOURCE if research["type"].startswith(CAUTIOUS_QUANTS) else "")
    return [
        token, lookup(FAMILY, research["family"]), bpw.group(1), imatrix,
        lookup(TIER, research["tier_le4b"]), lookup(TIER, research["tier_7_14b"]),
        lookup(TIER, research["tier_20_35b"]), lookup(TIER, research["tier_moe_small_active"]),
        research["tier_confidence"], source,
    ]


def quant_rows(path: Path) -> dict[str, list[str]]:
    rows: dict[str, list[str]] = {}
    for research in read_dicts(path):
        for token in quant_tokens(research["type"]) or []:
            rows[token] = quant_row(research, token)
    return rows


def clean(text: str) -> str:
    return " ".join(text.split())


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9.]+", "-", text.lower()).strip("-")


def declared_codes(declared: str) -> list[str]:
    stripped = re.sub(r"\([^)]*\)", "", declared)
    return [code.strip() for code in stripped.split(";") if re.fullmatch(r"[a-z]{2,3}", code.strip())]


def patterns_for(family: str, generation: str) -> list[str]:
    for (known_family, prefix), patterns in GENERATION_PATTERNS.items():
        if known_family == family and generation.startswith(prefix):
            return patterns
    match = re.match(r"([0-9][0-9.]*)", generation)
    if match is None:
        raise MappingError(f"no pattern for {family} | {generation}")
    return [f"{slug(family)}-{match.group(1)}"]


def language_verdict(research: dict[str, str]) -> tuple[str, list[str], str]:
    codes = declared_codes(research["declared_list"])
    kind = "COMPLETE" if codes else "NONE"
    status = research["uk_status"]
    if status == "ENGLISH_ONLY":
        kind, codes = "COMPLETE", ["en"]
    elif status in ("NO_STATEMENT", "CLAIMED_NO_LIST", "CONFLICT"):
        kind, codes = "NONE", []
    family = research["family"]
    if family.startswith(("MamayLM", "Lapa")):
        kind = "POSITIVE_ONLY"
    evidence = clean(research["uk_quality_evidence"])
    if family.startswith("MamayLM"):
        evidence = "BOOKLOOM-RUN:ru>uk; " + evidence
    return kind, codes, evidence


def language_rows(path: Path) -> dict[str, list[str]]:
    rows: dict[str, list[str]] = {}
    for research in read_dicts(path):
        kind, codes, evidence = language_verdict(research)
        source = clean(research["declared_where"])
        for pattern in patterns_for(research["family"], research["generation"]):
            add_row(rows, [slug(pattern), "PREFIX", pattern, kind, ";".join(codes), source, evidence])
    for hand in HAND_LANGUAGE_ROWS:
        add_row(rows, hand)
    return rows


def add_row(rows: dict[str, list[str]], row: list[str]) -> None:
    if row[0] in rows:
        raise MappingError(f"two research rows map to the key {row[0]!r}")
    rows[row[0]] = row


def read_bundle(path: Path) -> tuple[list[str], dict[str, list[str]], list[str]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    deliberate = [line[len(DELIBERATE_PREFIX):].strip() for line in lines if line.startswith(DELIBERATE_PREFIX)]
    body = list(csv.reader(line for line in lines if line and not line.startswith("#")))
    return body[0], {row[0]: row for row in body[1:]}, deliberate


def compact(text: str) -> str:
    return re.sub(r"[^a-z0-9]", "", text.lower())


def covered_terms(deliberate: list[str]) -> set[str]:
    terms: set[str] = set()
    for line in deliberate:
        words = line.split()
        for size in range(1, MAX_TERM_WORDS + 1):
            for start in range(len(words) - size + 1):
                terms.add(compact("".join(words[start:start + size])))
    return terms


def as_csv(values: list[str]) -> str:
    buffer = io.StringIO()
    csv.writer(buffer, lineterminator="").writerow(values)
    return buffer.getvalue()


def describe(label: str, key: str, research: list[str] | None, bundled: list[str] | None) -> str:
    if research is None:
        status = "only in bundle"
    elif bundled is None:
        status = "only in research"
    else:
        status = "changed"
    lines = [f"{label} {key}: {status}"]
    if research is not None:
        lines.append("  research " + as_csv(research))
    if bundled is not None:
        lines.append("  bundled  " + as_csv(bundled))
    return "\n".join(lines)


def report(label: str, research: dict[str, list[str]], bundled: dict[str, list[str]], covered: set[str]) -> int:
    changed = 0
    for key in [*research, *(k for k in bundled if k not in research)]:
        if compact(key) in covered:
            continue
        new, old = research.get(key), bundled.get(key)
        if new == old:
            continue
        print(describe(label, key, new, old))
        changed += 1
    return changed


def compare(label: str, bundle_name: str, columns: list[str], research: dict[str, list[str]]) -> int:
    header, bundled, deliberate = read_bundle(BUNDLE / bundle_name)
    if header != columns:
        raise MappingError(f"{bundle_name} header {header} differs from {columns}")
    return report(label, research, bundled, covered_terms(deliberate))


def find_research_dir(given: Path) -> Path | None:
    for candidate in (given, given / RESEARCH_SUBDIR):
        if (candidate / QUANT_FILE).is_file() and (candidate / LANGUAGE_FILE).is_file():
            return candidate
    return None


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Compare the bundled model-knowledge CSVs with the research data.")
    parser.add_argument("research_dir", type=Path, help="folder holding quant-types.csv and declared-languages.csv, or its root")
    args = parser.parse_args(argv)
    data_dir = find_research_dir(args.research_dir)
    if data_dir is None:
        print("research data not found")
        return 0
    try:
        changed = compare("quant-tiers.csv", QUANT_BUNDLE, QUANT_COLUMNS, quant_rows(data_dir / QUANT_FILE))
        changed += compare("declared-languages.csv", LANGUAGE_BUNDLE, LANGUAGE_COLUMNS,
                           language_rows(data_dir / LANGUAGE_FILE))
    except (MappingError, OSError) as failure:
        print(f"model-knowledge-check: {failure}", file=sys.stderr)
        return 2
    return 1 if changed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
