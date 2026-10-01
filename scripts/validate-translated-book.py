#!/usr/bin/env python3
"""Check a translated BookLoom export against the fixture it was translated from.

Usage:
    python3 scripts/validate-translated-book.py <txt|md|fb2|epub> <source_fixture> <translated_file> [--lang uk]
    python3 scripts/validate-translated-book.py --self-test

The source fixture's directory must hold the manifest.json written by scripts/make-fixtures.py. Blocks are matched
by id (EPUB, FB2 — the skeleton keeps every id) or by position (Markdown, TXT). Two families of checks run:

  structure  container validity, block inventory, inline markup per block (tags, links, images, footnote refs),
             code spans, verbatim and invisible blocks unchanged, images/fonts byte-identical, no leftover ⟦gN⟧
  language   target-script letters in every translatable block, no long untranslated runs, glossary names rendered
             one way across the book, declared language updated

Prints one line per check and a final PASS/FAIL; exits 0 on PASS, 1 on FAIL, 2 on a usage error.
Standard library only.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
import tempfile
import unicodedata
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

XHTML_TAGS = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "td", "th", "figcaption", "pre"}
FB2_TAGS = {"p", "v", "subtitle", "td", "th", "text-author"}
XLINK_HREF = "{http://www.w3.org/1999/xlink}href"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
TEXT_ENTRY = re.compile(r"\.(xhtml|html|htm|opf|ncx|xml)$", re.IGNORECASE)
INVISIBLE = "\u00a0\u200b\u200c\u200d\u2060\ufeff"
CYRILLIC_TARGETS = {"uk", "ru", "be", "bg", "sr", "mk", "kk", "ky", "tg", "mn"}
GREEK_TARGETS = {"el"}
LEFTOVER_FAIL_WORDS = 6
LEFTOVER_WARN_WORDS = 4
LATIN_TO_CYRILLIC_INITIALS = {
    "a": "аеє", "b": "б", "c": "кцс", "d": "д", "e": "еєеіиа", "f": "ф", "g": "гґдж", "h": "гх", "i": "іиай",
    "j": "дйж", "k": "к", "l": "л", "m": "м", "n": "н", "o": "оа", "p": "п", "q": "к", "r": "р", "s": "сш",
    "t": "тч", "u": "уюа", "v": "в", "w": "ву", "x": "кз", "y": "йиі", "z": "з",
}


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def norm(text: str) -> str:
    return re.sub(r"[ \t\r\n\f]+", " ", text).strip(" \t\r\n\f")


def visible(text: str) -> str:
    return "".join(ch for ch in norm(text) if ch not in INVISIBLE).strip()


@dataclass
class Block:
    id: str
    text: str
    lang_text: str
    markup: Counter
    codes: list[str] = field(default_factory=list)
    foreign: list[str] = field(default_factory=list)
    lines: int = 1
    indent: str = ""


@dataclass
class Book:
    fmt: str
    path: Path
    blocks: list[Block] = field(default_factory=list)
    images: list[str] = field(default_factory=list)
    binaries: dict[str, bytes] = field(default_factory=dict)
    meta_lang: str | None = None
    aux: dict[str, list[str]] = field(default_factory=dict)
    raw: list[tuple[str, str]] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


# --------------------------------------------------------------------------------------------------------------
# XML (EPUB XHTML and FB2) blocks
# --------------------------------------------------------------------------------------------------------------

def xml_signature(el: ET.Element, fmt: str) -> str:
    name = local(el.tag)
    if name == "a":
        href = el.get("href") if fmt == "epub" else el.get(XLINK_HREF)
        return f"a[href={href}]" + (f"[type={el.get('type')}]" if el.get("type") else "")
    if name == "img":
        return f"img[src={el.get('src')}]"
    if name == "image":
        return f"image[href={el.get(XLINK_HREF)}]"
    lang = el.get("lang") or el.get(XML_LANG)
    cls = el.get("class") or el.get("name")
    return name + (f"[class={cls}]" if cls else "") + (f"[lang={lang}]" if lang else "")


def xml_block(el: ET.Element, fmt: str, fallback_id: str) -> Block:
    markup: Counter = Counter()
    codes: list[str] = []
    foreign: list[str] = []
    for d in el.iter():
        if d is el:
            continue
        markup[xml_signature(d, fmt)] += 1
        if local(d.tag) == "code":
            codes.append("".join(d.itertext()))
        elif d.get("lang") or d.get(XML_LANG):
            foreign.append(norm("".join(d.itertext())))
    if local(el.tag) == "pre":
        codes.append("".join(el.itertext()))
    return Block(el.get("id") or fallback_id, norm("".join(el.itertext())), norm(_lang_text(el, top=True)),
                 markup, codes, foreign)


def _lang_text(el: ET.Element, top: bool = False) -> str:
    """The text a translation is judged on: code and runs declared in another language are left out."""
    if not top and (local(el.tag) == "code" or el.get("lang") or el.get(XML_LANG)):
        return ""
    if local(el.tag) == "pre":
        return ""
    out = [el.text or ""]
    for child in el:
        out.append(_lang_text(child))
        out.append(child.tail or "")
    return "".join(out)


def collect_blocks(root: ET.Element, tags: set[str], fmt: str, book: Book) -> None:
    def visit(el: ET.Element) -> None:
        name = local(el.tag)
        if name in ("img", "image"):
            book.images.append(xml_signature(el, fmt))
            alt = el.get("alt")
            if alt is not None:
                book.aux.setdefault("image_alts", []).append(alt)
        if name in tags:
            book.blocks.append(xml_block(el, fmt, f"#{len(book.blocks) + 1}"))
            for d in el.iter():
                if d is not el and local(d.tag) in ("img", "image"):
                    book.images.append(xml_signature(d, fmt))
                    if d.get("alt") is not None:
                        book.aux.setdefault("image_alts", []).append(d.get("alt"))
            return
        for child in el:
            visit(child)

    visit(root)


# --------------------------------------------------------------------------------------------------------------
# Parsers per format
# --------------------------------------------------------------------------------------------------------------

def parse_epub(path: Path) -> Book:
    book = Book("epub", path)
    try:
        archive = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError) as error:
        book.errors.append(f"not a zip archive: {error}")
        return book
    with archive:
        infos = archive.infolist()
        first = infos[0] if infos else None
        if first is None or first.filename != "mimetype" or first.compress_type != zipfile.ZIP_STORED:
            book.errors.append("mimetype is not the first, STORED entry")
        elif archive.read("mimetype") != b"application/epub+zip":
            book.errors.append("mimetype content is not application/epub+zip")
        _read_epub_entries(archive, book)
    return book


def _read_epub_entries(archive: zipfile.ZipFile, book: Book) -> None:
    texts: dict[str, bytes] = {}
    for info in archive.infolist():
        data = archive.read(info.filename)
        if info.filename == "mimetype":
            continue
        if TEXT_ENTRY.search(info.filename):
            texts[info.filename] = data
            book.raw.append((info.filename, data.decode("utf-8", errors="replace")))
        else:
            book.binaries[info.filename] = data
    trees: dict[str, ET.Element] = {}
    for name, data in texts.items():
        try:
            trees[name] = ET.fromstring(data)
        except ET.ParseError as error:
            book.errors.append(f"{name}: not well-formed XML ({error})")
    container = trees.get("META-INF/container.xml")
    if container is None:
        book.errors.append("META-INF/container.xml missing")
        return
    opf_path = next((e.get("full-path") for e in container.iter() if local(e.tag) == "rootfile"), None)
    opf = trees.get(opf_path or "")
    if opf is None:
        book.errors.append(f"package document {opf_path} missing or unreadable")
        return
    _read_opf(opf, opf_path, trees, book)


def _read_opf(opf: ET.Element, opf_path: str, trees: dict[str, ET.Element], book: Book) -> None:
    base = opf_path.rsplit("/", 1)[0] + "/" if "/" in opf_path else ""
    items = {e.get("id"): e for e in opf.iter() if local(e.tag) == "item"}
    book.meta_lang = next((norm(e.text or "") for e in opf.iter() if local(e.tag) == "language"), None)
    for key in ("title", "creator", "description"):
        book.aux[f"dc_{key}"] = [norm(e.text or "") for e in opf.iter() if local(e.tag) == key]
    spine = [e.get("idref") for e in opf.iter() if local(e.tag) == "itemref"]
    book.aux["spine"] = [str(len(spine))]
    for idref in spine:
        item = items.get(idref)
        doc = trees.get(base + item.get("href")) if item is not None else None
        if doc is None:
            book.errors.append(f"spine item {idref} missing")
            continue
        book.aux.setdefault("head_titles", []).extend(
            norm("".join(e.itertext())) for e in doc.iter() if local(e.tag) == "title")
        body = next((e for e in doc.iter() if local(e.tag) == "body"), None)
        if body is not None:
            collect_blocks(body, XHTML_TAGS, "epub", book)
    for item in items.values():
        tree = trees.get(base + (item.get("href") or ""))
        if tree is None:
            continue
        if "nav" in (item.get("properties") or ""):
            book.aux["nav_labels"] = [norm("".join(a.itertext())) for a in tree.iter() if local(a.tag) == "a"]
        if item.get("media-type") == "application/x-dtbncx+xml":
            book.aux["ncx_labels"] = [norm(t.text or "") for t in tree.iter() if local(t.tag) == "text"]


def parse_fb2(path: Path) -> Book:
    book = Book("fb2", path)
    data = path.read_bytes()
    book.raw.append((path.name, data.decode("utf-8", errors="replace")))
    try:
        root = ET.fromstring(data)
    except ET.ParseError as error:
        book.errors.append(f"not well-formed XML ({error})")
        return book
    if local(root.tag) != "FictionBook":
        book.errors.append(f"root element is {local(root.tag)}, not FictionBook")
    import base64
    for el in root:
        name = local(el.tag)
        if name == "binary":
            book.binaries[el.get("id")] = base64.b64decode("".join((el.text or "").split()))
        elif name == "body":
            collect_blocks(el, FB2_TAGS, "fb2", book)
        elif name == "description":
            _read_fb2_description(el, book)
    return book


def _read_fb2_description(desc: ET.Element, book: Book) -> None:
    info = next((e for e in desc if local(e.tag) == "title-info"), None)
    if info is None:
        book.errors.append("description/title-info missing")
        return
    for el in info:
        name = local(el.tag)
        if name == "lang":
            book.meta_lang = norm(el.text or "")
        elif name == "book-title":
            book.aux["book_title"] = [norm(el.text or "")]
        elif name == "author":
            book.aux.setdefault("author", []).extend(norm(e.text or "") for e in el)
        elif name == "annotation":
            book.aux["annotation"] = [norm("".join(p.itertext())) for p in el]
        elif name == "coverpage":
            book.images.extend(xml_signature(e, "fb2") for e in el.iter() if local(e.tag) == "image")


LINK = re.compile(r"\[((?:\\.|[^\]\\])*)\]\(([^)\s]*)\)")


def md_inline(s: str, foreign_texts: list[str] | None = None) -> tuple[str, str, Counter, list[str], list[tuple]]:
    """(text, text without code, markup counter, code spans, images as (src, alt)) of one Markdown block."""
    out, lang_out, codes, images = [], [], [], []
    markup: Counter = Counter()
    i = 0
    while i < len(s):
        ch = s[i]
        if ch == "\\" and i + 1 < len(s):
            piece = " " if s[i + 1] == "\n" else s[i + 1]
            out.append(piece)
            lang_out.append(piece)
            i += 2
            continue
        if ch == "`" and s.find("`", i + 1) > 0:
            j = s.find("`", i + 1)
            codes.append(s[i + 1:j])
            out.append(s[i + 1:j])
            markup["code"] += 1
            i = j + 1
            continue
        match = LINK.match(s, i + 1) if s.startswith("![", i) else LINK.match(s, i) if ch == "[" else None
        if match:
            inner = md_inline(match.group(1))
            if ch == "!":
                images.append((match.group(2), inner[0]))
                markup[f"img[src={match.group(2)}]"] += 1
            else:
                out.append(inner[0])
                lang_out.append(inner[1])
                markup.update(inner[2])
                markup[f"a[href={match.group(2)}]"] += 1
            i = match.end()
            continue
        if s.startswith("**", i):
            markup["strong-delimiter"] += 1
            i += 2
            continue
        if ch in "*_":
            markup["em-delimiter"] += 1
            i += 1
            continue
        out.append(ch)
        lang_out.append(ch)
        i += 1
    return norm("".join(out)), norm("".join(lang_out)), markup, codes, images


def parse_md(path: Path) -> Book:
    book = Book("md", path)
    try:
        text = path.read_bytes().decode("utf-8")
    except UnicodeDecodeError as error:
        book.errors.append(f"not UTF-8: {error}")
        return book
    book.raw.append((path.name, text))
    lines = text.split("\n")
    start = _md_frontmatter(lines, book)
    _md_blocks(lines[start:], book)
    return book


def _md_frontmatter(lines: list[str], book: Book) -> int:
    if not lines or lines[0] != "---":
        book.errors.append("frontmatter missing")
        return 0
    end = lines.index("---", 1)
    for line in lines[1:end]:
        key, _, value = line.partition(":")
        value = value.strip().strip('"')
        if key == "lang":
            book.meta_lang = value
        else:
            book.aux.setdefault("frontmatter", []).append(value)
    return end + 1


def _md_blocks(lines: list[str], book: Book) -> None:
    para: list[str] = []
    fence: list[str] | None = None

    def flush() -> None:
        if para:
            _md_add(book, "\n".join(para), len(para))
            para.clear()

    for line in lines:
        if fence is not None:
            if line.startswith("```"):
                book.blocks.append(Block(f"#{len(book.blocks) + 1}", norm("\n".join(fence)), "", Counter({"pre": 1}),
                                         ["\n".join(fence)]))
                fence = None
            else:
                fence.append(line)
            continue
        if line.strip(" \t") == "":
            flush()
        elif line.startswith("```"):
            flush()
            fence = []
        elif re.match(r"#{1,6} ", line):
            flush()
            _md_add(book, line.split(" ", 1)[1], 1)
        elif line.startswith("|"):
            flush()
            if not re.fullmatch(r"\|(\s*:?-+:?\s*\|)+", line):
                for cell in re.split(r"(?<!\\)\|", line)[1:-1]:
                    _md_add(book, cell.strip(), 1)
        elif line.startswith(">"):
            content = line[1:].lstrip(" ")
            if content:
                para.append(content)
            else:
                flush()
        elif re.match(r"(\d+\.|[-*+]) ", line):
            flush()
            _md_add(book, line.split(" ", 1)[1], 1)
        else:
            para.append(line)
    flush()


def _md_add(book: Book, raw: str, lines: int) -> None:
    text, lang_text, markup, codes, images = md_inline(raw)
    for src, alt in images:
        book.images.append(f"img[src={src}]")
        book.aux.setdefault("image_alts", []).append(alt)
    book.blocks.append(Block(f"#{len(book.blocks) + 1}", text, lang_text, markup, codes, [], lines))


def parse_txt(path: Path) -> Book:
    book = Book("txt", path)
    try:
        text = path.read_bytes().decode("utf-8")
    except UnicodeDecodeError as error:
        book.errors.append(f"not UTF-8: {error}")
        return book
    book.raw.append((path.name, text))
    paragraph: list[str] = []
    for line in text.split("\n") + [""]:
        if line.strip(" \t\r\f") == "":
            if paragraph:
                body = "\n".join(paragraph)
                indent = paragraph[0][: len(paragraph[0]) - len(paragraph[0].lstrip(" \t"))]
                markup = Counter({"note-ref": len(re.findall(r"\[\d+\]", body))})
                book.blocks.append(Block(f"#{len(book.blocks) + 1}", norm(body), norm(body), +markup, [], [],
                                         len(paragraph), indent))
                paragraph = []
        else:
            paragraph.append(line)
    return book


PARSERS = {"epub": parse_epub, "fb2": parse_fb2, "md": parse_md, "txt": parse_txt}


# --------------------------------------------------------------------------------------------------------------
# Checks
# --------------------------------------------------------------------------------------------------------------

@dataclass
class Result:
    name: str
    family: str
    status: str  # PASS | FAIL | WARN | SKIP
    detail: str


def ids_list(ids: list[str], limit: int = 12) -> str:
    shown = ", ".join(ids[:limit])
    return shown + (f" … (+{len(ids) - limit})" if len(ids) > limit else "")


class Validator:
    def __init__(self, fmt: str, source: Path, translated: Path, lang: str | None, manifest: dict) -> None:
        self.fmt, self.lang, self.manifest = fmt, lang, manifest
        self.expected = manifest["formats"][fmt]["blocks"]
        self.src = PARSERS[fmt](source)
        self.out = PARSERS[fmt](translated)
        self.results: list[Result] = []
        self.pairs = self._pair_blocks()

    def add(self, name: str, family: str, failures: list[str], ok: str, warn: bool = False) -> None:
        status = "PASS" if not failures else ("WARN" if warn else "FAIL")
        self.results.append(Result(name, family, status, ok if not failures else ids_list(failures)))

    def _pair_blocks(self) -> list[tuple[dict, Block | None, Block | None]]:
        by_pos = self.fmt in ("md", "txt")
        src_map = {b.id: b for b in self.src.blocks}
        out_map = {b.id: b for b in self.out.blocks}
        pairs = []
        for i, exp in enumerate(self.expected):
            if by_pos:
                s = self.src.blocks[i] if i < len(self.src.blocks) else None
                o = self.out.blocks[i] if i < len(self.out.blocks) else None
            else:
                s, o = src_map.get(exp["id"]), out_map.get(exp["id"])
            pairs.append((exp, s, o))
        return pairs

    def run(self) -> list[Result]:
        self.check_container()
        self.check_inventory()
        self.check_per_block()
        self.check_images_and_binaries()
        self.check_tokens()
        if self.lang:
            self.check_language()
            self.check_names()
            if self.fmt != "txt":  # a plain-text file declares no language
                self.check_metadata_language()
        self.report_aux()
        return self.results

    # ---- structure ----
    def check_container(self) -> None:
        self.add("container", "structure", self.out.errors, f"{self.fmt} opens and parses")

    def check_inventory(self) -> None:
        failures = []
        if len(self.out.blocks) != len(self.src.blocks):
            failures.append(f"block count {len(self.out.blocks)} != source {len(self.src.blocks)}")
        if self.fmt in ("epub", "fb2"):
            src_ids = [b.id for b in self.src.blocks]
            out_ids = [b.id for b in self.out.blocks]
            missing = [i for i in src_ids if i not in set(out_ids)]
            extra = [i for i in out_ids if i not in set(src_ids)]
            failures += [f"missing {m}" for m in missing] + [f"extra {e}" for e in extra]
            if not missing and not extra and src_ids != out_ids:
                failures.append("block order differs")
        if self.fmt == "epub" and self.out.aux.get("spine") != self.src.aux.get("spine"):
            failures.append("spine length differs")
        for key in ("nav_labels", "ncx_labels"):
            if len(self.out.aux.get(key, [])) != len(self.src.aux.get(key, [])):
                failures.append(f"{key} count differs")
        self.add("blocks", "structure", failures, f"{len(self.out.blocks)} blocks, same ids/order as the source")

    def check_per_block(self) -> None:
        markup, code, verbatim, invisible, lines, foreign = [], [], [], [], [], []
        for exp, s, o in self.pairs:
            if s is None or o is None:
                continue
            if s.markup != o.markup:
                diff = (o.markup - s.markup) + Counter({f"-{k}": v for k, v in (s.markup - o.markup).items()})
                markup.append(f"{exp['id']} ({' '.join(f'{k}×{v}' for k, v in diff.items())})")
            if s.codes != o.codes:
                code.append(exp["id"])
            if s.lines != o.lines and exp["kind"] in ("list", "table", "stanza"):
                lines.append(f"{exp['id']} lines {o.lines}!={s.lines}")
            if s.indent != o.indent:
                lines.append(f"{exp['id']} indent changed")
            cat = exp["category"]
            if cat in ("verbatim", "code") and o.text != s.text:
                verbatim.append(f"{exp['id']} ({s.text!r} -> {o.text!r})")
            if cat in ("invisible", "image") and visible(o.text):
                invisible.append(f"{exp['id']} gained text {o.text[:30]!r}")
            if (cat == "foreign" and o.text != s.text) or s.foreign != o.foreign:
                foreign.append(exp["id"])
        self.add("markup", "structure", markup, "inline markup, links and footnote refs identical per block")
        self.add("code", "structure", code, "code spans and blocks unchanged")
        self.add("verbatim", "structure", verbatim, "numbers-only/symbol-only/code-only blocks unchanged")
        self.add("invisible", "structure", invisible, "NBSP/ZWSP-only and image-only blocks still carry no text")
        self.add("layout", "structure", lines, "line structure of lists/tables/verse and indentation kept")
        self.add("foreign", "structure", foreign, "Latin and French passages kept as written (Keep policy)",
                 warn=True)

    def check_images_and_binaries(self) -> None:
        failures = []
        if self.out.images != self.src.images:
            failures.append(f"image references differ ({len(self.out.images)} vs {len(self.src.images)})")
        if self.fmt == "md":
            failures += self._md_image_files()
        for name, data in self.src.binaries.items():
            got = self.out.binaries.get(name)
            if got is None:
                failures.append(f"{name} missing")
            elif got != data:
                failures.append(f"{name} bytes differ")
        detail = f"{len(self.out.images)} image refs, {len(self.src.binaries)} binary entries byte-identical"
        self.add("binaries", "structure", failures, detail)

    def _md_image_files(self) -> list[str]:
        failures = []
        for ref in sorted(set(self.src.images)):
            rel = ref[len("img[src="):-1]
            src_file, out_file = self.src.path.parent / rel, self.out.path.parent / rel
            if out_file.exists() and src_file.exists() and out_file.read_bytes() != src_file.read_bytes():
                failures.append(f"{rel} bytes differ")
        return failures

    def check_tokens(self) -> None:
        hits = [f"{exp['id']}" for exp, _, o in self.pairs if o is not None and re.search("[⟦⟧]", o.text)]
        if not hits:
            hits = [name for name, text in self.out.raw if re.search("[⟦⟧]", text)]
        self.add("tokens", "structure", hits, "no ⟦gN⟧ placeholder left anywhere")

    # ---- language ----
    def _script_ok(self, text: str) -> bool:
        if self.lang in CYRILLIC_TARGETS:
            return any(unicodedata.name(ch, "").startswith("CYRILLIC") for ch in text)
        if self.lang in GREEK_TARGETS:
            return any(unicodedata.name(ch, "").startswith("GREEK") for ch in text)
        return True

    def _judged_text(self, block: Block) -> str:
        text = block.lang_text
        for item in self.manifest["foreign"]["inline"]:
            text = text.replace(item["text"], " ")
        return text

    def check_language(self) -> None:
        latin_target = self.lang not in CYRILLIC_TARGETS | GREEK_TARGETS
        no_script, unchanged, leftover_fail, leftover_warn = [], [], [], []
        allowed = {f.lower() for n in self.manifest["names"] for f in n["forms"]} | {"dr", "km", "kg", "mgal"}
        for exp, s, o in self.pairs:
            if exp["category"] != "translatable" or s is None or o is None:
                continue
            judged = self._judged_text(o)
            if not latin_target and not self._script_ok(judged):
                no_script.append(exp["id"])
            elif latin_target and o.text == s.text:
                unchanged.append(exp["id"])
            run = longest_latin_run(judged, allowed)
            if not latin_target and run >= LEFTOVER_FAIL_WORDS:
                leftover_fail.append(f"{exp['id']}({run} words)")
            elif not latin_target and run >= LEFTOVER_WARN_WORDS:
                leftover_warn.append(f"{exp['id']}({run} words)")
        translatable = sum(1 for e in self.expected if e["category"] == "translatable")
        if latin_target:
            self.add("target-script", "language", unchanged, f"{translatable} translatable blocks changed")
        else:
            self.add("target-script", "language", no_script,
                     f"{translatable} translatable blocks all carry {self.lang} script letters")
        self.add("leftover-source", "language", leftover_fail, f"no run of ≥{LEFTOVER_FAIL_WORDS} source words")
        self.add("leftover-source-short", "language", leftover_warn,
                 f"no run of ≥{LEFTOVER_WARN_WORDS} source words", warn=True)

    def check_names(self) -> None:
        failures, details = [], []
        candidates = [(e, s, o) for e, s, o in self.pairs
                      if e["category"] == "translatable" and s is not None and o is not None]
        texts = {e["id"]: self._judged_text(o) for e, _, o in candidates}
        for name in self.manifest["names"]:
            for form in name["forms"]:
                pattern = re.compile(rf"(?<![\w]){re.escape(form)}(?![\w])")
                hit_ids = [e["id"] for e, s, _ in candidates if pattern.search(self._judged_text(s))]
                if not hit_ids:
                    continue
                verdict = name_rendering(form, hit_ids, texts, self.lang)
                details.append(f"{form}→{'/'.join(verdict['surfaces'][:4]) or '?'}")
                if verdict["missing"]:
                    failures.append(f"{form}: not rendered as '{verdict['stem']}…' in {ids_list(verdict['missing'], 6)}")
        self.add("names", "language", failures, "; ".join(details))

    def check_metadata_language(self) -> None:
        declared = (self.out.meta_lang or "").lower()
        ok = declared == self.lang or declared.startswith(self.lang + "-")
        failures = [] if ok else [f"declared language is {self.out.meta_lang!r}, expected {self.lang!r}"]
        self.add("declared-language", "language", failures, f"declared language {declared}", warn=self.fmt == "md")

    def report_aux(self) -> None:
        changed, total = 0, 0
        for key, values in self.src.aux.items():
            if key == "spine":
                continue
            for i, value in enumerate(values):
                total += 1
                got = self.out.aux.get(key, [])
                changed += int(i < len(got) and got[i] != value)
        self.results.append(Result("auxiliary", "info", "INFO", f"{changed}/{total} auxiliary texts changed "
                                   "(titles, authors, alt text, nav/NCX labels, frontmatter; behind 'Also translate')"))


def longest_latin_run(text: str, allowed: set[str]) -> int:
    best = run = 0
    for word in re.findall(r"[^\W\d_]+", text):
        if all("LATIN" in unicodedata.name(ch, "") for ch in word) and len(word) > 1 and word.lower() not in allowed:
            run += 1
            best = max(best, run)
        else:
            run = 0
    return best


def stems_of(text: str) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    for token in re.findall(r"[^\W\d_]+", text):
        if len(token) >= 3:
            out.setdefault(token.lower()[:4], []).append(token)
    return out


def name_rendering(form: str, hit_ids: list[str], texts: dict[str, str], lang: str | None) -> dict:
    """Finds the stem that renders `form`: the one most specific to the blocks the form occurs in (present there,
    rare elsewhere), nudged toward stems starting with a plausible transliteration of the form's first letter."""
    hits = set(hit_ids)
    stems = {bid: stems_of(t) for bid, t in texts.items()}
    others = [bid for bid in texts if bid not in hits]
    initials = LATIN_TO_CYRILLIC_INITIALS.get(form[0].lower(), "") + form[0].lower()
    best, best_score = "", -9.0
    pool = {stem for bid in hit_ids for stem in stems.get(bid, {})}
    for stem in sorted(pool):
        inside = sum(1 for bid in hit_ids if stem in stems.get(bid, {})) / len(hit_ids)
        outside = sum(1 for bid in others if stem in stems.get(bid, {})) / max(1, len(others))
        if outside > 0.25:
            continue
        score = inside - outside + (0.05 if stem[0] in initials else 0.0)
        if score > best_score:
            best, best_score = stem, score
    surfaces = Counter(tok for bid in hit_ids for tok in stems.get(bid, {}).get(best, []))
    missing = [bid for bid in hit_ids if best not in stems.get(bid, {})]
    return {"stem": best, "surfaces": [s for s, _ in surfaces.most_common()], "missing": missing}


# --------------------------------------------------------------------------------------------------------------
# Report and CLI
# --------------------------------------------------------------------------------------------------------------

def validate(fmt: str, source: Path, translated: Path, lang: str | None, quiet: bool = False) -> list[Result]:
    manifest = json.loads((source.parent / "manifest.json").read_text(encoding="utf-8"))
    results = Validator(fmt, source, translated, lang, manifest).run()
    if not quiet:
        print(f"validate-translated-book: {fmt}  source={source}  translated={translated}  target={lang}")
        for r in results:
            print(f"{r.status:<5} {r.family:<9} {r.name:<22} {r.detail}")
        print(f"RESULT: {overall(results)}  (structure: {family_status(results, 'structure')}, "
              f"language: {family_status(results, 'language')})")
    return results


def family_status(results: list[Result], family: str) -> str:
    rows = [r for r in results if r.family == family]
    if not rows:
        return "SKIP"
    return "FAIL" if any(r.status == "FAIL" for r in rows) else "PASS"


def overall(results: list[Result]) -> str:
    return "FAIL" if any(r.status == "FAIL" for r in results) else "PASS"


def load_generator():
    sys.dont_write_bytecode = True  # keep scripts/ free of __pycache__
    path = Path(__file__).resolve().parent / "make-fixtures.py"
    spec = importlib.util.spec_from_file_location("make_fixtures", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules["make_fixtures"] = module
    spec.loader.exec_module(module)
    return module


TRANSLIT = dict(zip("abcdefghijklmnopqrstuvwxyz", "абцдефгхійклмнопкрстуввксиз"))


def translit(text: str) -> str:
    """A stand-in 'translation': every ASCII letter becomes a Cyrillic one, case kept, everything else as is."""
    out = []
    for ch in text:
        low = TRANSLIT.get(ch.lower())
        out.append(ch if low is None else (low.upper() if ch.isupper() else low))
    return "".join(out)


def self_test() -> int:
    gen = load_generator()
    fixtures = gen.DEFAULT_OUT
    manifest = json.loads((fixtures / "manifest.json").read_text(encoding="utf-8"))
    problems: list[str] = []
    expect = Expectations(problems)
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        regenerated = gen.render_all()
        drift = [rel for rel, data in regenerated.items() if (fixtures / rel).read_bytes() != data]
        expect.true("fixtures match the generator (no drift)", not drift, drift)
        for fmt in gen.FORMATS:
            expect_inventory(expect, fmt, fixtures, manifest)
            source = fixtures / f"{gen.BASENAME}.{fmt}"
            identity = validate(fmt, source, source, "uk", quiet=True)
            expect.true(f"{fmt}: identity passes structure", family_status(identity, "structure") == "PASS", identity)
            expect.true(f"{fmt}: identity fails language", family_status(identity, "language") == "FAIL", identity)
        fake_dir = tmp_path / "fake"
        gen.write_all(fake_dir, gen.render_all(tx=translit, lang="uk"))
        for fmt in gen.FORMATS:
            result = validate(fmt, fixtures / f"{gen.BASENAME}.{fmt}", fake_dir / f"{gen.BASENAME}.{fmt}", "uk",
                              quiet=True)
            expect.true(f"{fmt}: transliterated stand-in translation passes", overall(result) == "PASS", result)
        run_mutations(expect, gen, fixtures, fake_dir, tmp_path)
        expect_inflection_tolerated(expect, gen, fixtures, fake_dir, tmp_path)
        integrity = fixture_integrity(fixtures, gen.BASENAME)
        expect.true("fixtures are internally consistent (unique ids, every link/image/fragment resolves)",
                    not integrity, integrity[:8])
    for line in expect.lines:
        print(line)
    print(f"SELF-TEST: {'FAIL' if problems else 'PASS'} ({len(expect.lines) - len(problems)}/{len(expect.lines)})")
    return 1 if problems else 0


class Expectations:
    def __init__(self, problems: list[str]) -> None:
        self.problems = problems
        self.lines: list[str] = []

    def true(self, label: str, condition: bool, evidence=None) -> None:
        self.lines.append(f"{'ok  ' if condition else 'FAIL'}  {label}")
        if not condition:
            self.problems.append(label)
            for r in evidence if isinstance(evidence, list) else [evidence]:
                if isinstance(r, Result) and r.status in ("FAIL", "WARN"):
                    self.lines.append(f"        {r.status} {r.name}: {r.detail}")
                elif not isinstance(r, Result) and r:
                    self.lines.append(f"        {r}")


def expect_inventory(expect: Expectations, fmt: str, fixtures: Path, manifest: dict) -> None:
    book = PARSERS[fmt](fixtures / f"earth-gravity.{fmt}")
    blocks = manifest["formats"][fmt]["blocks"]
    by_id = fmt in ("epub", "fb2")
    mismatched = [f"{e['id']}: manifest {e['text'][:40]!r} parsed {b.text[:40]!r}"
                  for e, b in zip(blocks, book.blocks) if e["text"] != b.text or (by_id and e["id"] != b.id)]
    if len(blocks) != len(book.blocks):
        mismatched.append(f"count manifest {len(blocks)} parsed {len(book.blocks)}")
    expect.true(f"{fmt}: parsed source matches the manifest inventory", not mismatched, mismatched[:5])
    images = manifest["formats"][fmt]["images"]
    expect.true(f"{fmt}: image count {images}", len(book.images) == images, [f"parsed {len(book.images)}"])


def run_mutations(expect: Expectations, gen, fixtures: Path, fake_dir: Path, tmp: Path) -> None:
    base = gen.BASENAME
    md = (fake_dir / f"{base}.md").read_text(encoding="utf-8")
    txt = (fake_dir / f"{base}.txt").read_text(encoding="utf-8")
    fb2 = (fake_dir / f"{base}.fb2").read_text(encoding="utf-8")
    first_vance = fb2.index(translit("Vance"), fb2.index('id="ch1-p1"'))
    cases = [
        ("md", "tokens", md.replace(translit("Gravity"), "⟦g1⟧" + translit("Gravity"), 1)),
        ("txt", "verbatim", txt.replace("\n\nXIV\n\n", "\n\nЧотирнадцять\n\n", 1)),
        ("txt", "target-script", txt.replace(translit(gen.REPEATED), gen.REPEATED, 1)),
        ("fb2", "names", fb2[:first_vance] + "Венс" + fb2[first_vance + len(translit("Vance")):]),
        ("fb2", "code", fb2.replace("<code>g = 9.81 m/s²</code>", "<code>g = 9,81 м/с²</code>", 1)),
        ("epub", "markup", epub_edit(fake_dir / f"{base}.epub", "OEBPS/text/ch1.xhtml",
                                     lambda s: s.replace("<strong>", "", 1).replace("</strong>", "", 1))),
        ("epub", "binaries", epub_edit(fake_dir / f"{base}.epub", "OEBPS/images/diagram.png",
                                       lambda b: b[:-1] + bytes([b[-1] ^ 1]))),
    ]
    for i, (fmt, check, content) in enumerate(cases):
        target = tmp / f"mutation{i}" / f"{base}.{fmt}"
        target.parent.mkdir(parents=True)
        if isinstance(content, bytes):
            target.write_bytes(content)
        else:
            target.write_text(content, encoding="utf-8")
        results = validate(fmt, fixtures / f"{base}.{fmt}", target, "uk", quiet=True)
        failed = {r.name for r in results if r.status == "FAIL"}
        expect.true(f"{fmt}: mutation caught by '{check}'", check in failed,
                    [f"failed checks: {sorted(failed)}"])


def expect_inflection_tolerated(expect: Expectations, gen, fixtures: Path, fake_dir: Path, tmp: Path) -> None:
    """A name declined by case (Ванце → Ванцем, Нелл → Нелли) is still one rendering: only the stem must agree."""
    fb2 = (fake_dir / f"{gen.BASENAME}.fb2").read_text(encoding="utf-8")
    at = fb2.index('id="ch4-p1"')
    declined = fb2[:at] + fb2[at:].replace(translit("Vance"), translit("Vance") + "м", 1)
    declined = declined.replace(translit("Nell") + " ", translit("Nell") + "и ", 1)
    target = tmp / "inflected" / f"{gen.BASENAME}.fb2"
    target.parent.mkdir(parents=True)
    target.write_text(declined, encoding="utf-8")
    results = validate("fb2", fixtures / f"{gen.BASENAME}.fb2", target, "uk", quiet=True)
    expect.true("fb2: names declined by case still count as one rendering", overall(results) == "PASS", results)


def fixture_integrity(fixtures: Path, base: str) -> list[str]:
    problems = _epub_integrity(fixtures / f"{base}.epub") + _fb2_integrity(fixtures / f"{base}.fb2")
    md = (fixtures / f"{base}.md").read_text(encoding="utf-8")
    problems += [f"md image {ref} missing" for ref in re.findall(r"!\[[^\]]*\]\(([^)]+)\)", md)
                 if not (fixtures / ref).is_file()]
    return problems


def _epub_integrity(path: Path) -> list[str]:
    problems: list[str] = []
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        docs = {n: ET.fromstring(archive.read(n)) for n in names if n.endswith(".xhtml")}
    ids = {n: [e.get("id") for e in d.iter() if e.get("id")] for n, d in docs.items()}
    for name, doc_ids in ids.items():
        problems += [f"{name}: duplicate id {i}" for i, c in Counter(doc_ids).items() if c > 1]
    for name, doc in docs.items():
        folder = name.rsplit("/", 1)[0]
        for el in doc.iter():
            ref = el.get("href") or el.get("src")
            if not ref or ref.startswith(("http://", "https://")):
                continue
            file_part, _, fragment = ref.partition("#")
            target = name if not file_part else _resolve(folder, file_part)
            if target not in names:
                problems.append(f"{name}: {ref} does not resolve")
            elif fragment and fragment not in ids.get(target, []):
                problems.append(f"{name}: #{fragment} not found in {target}")
    return problems


def _resolve(folder: str, ref: str) -> str:
    parts = folder.split("/") if folder else []
    for piece in ref.split("/"):
        if piece == "..":
            parts.pop()
        elif piece != ".":
            parts.append(piece)
    return "/".join(parts)


def _fb2_integrity(path: Path) -> list[str]:
    root = ET.fromstring(path.read_bytes())
    all_ids = [e.get("id") for e in root.iter() if e.get("id")]
    problems = [f"fb2: duplicate id {i}" for i, c in Counter(all_ids).items() if c > 1]
    for el in root.iter():
        ref = el.get(XLINK_HREF)
        if ref and ref.startswith("#") and ref[1:] not in all_ids:
            problems.append(f"fb2: {ref} does not resolve")
    return problems


def epub_edit(path: Path, entry: str, change) -> bytes:
    gen = sys.modules["make_fixtures"]
    with zipfile.ZipFile(path) as archive:
        entries = []
        for info in archive.infolist():
            data = archive.read(info.filename)
            if info.filename == entry:
                data = change(data) if entry.endswith(".png") else change(data.decode()).encode()
            entries.append((info.filename, data))
    return gen.write_zip(entries)


def main(argv: list[str]) -> int:
    if "--self-test" in argv:
        return self_test()
    parser = argparse.ArgumentParser(description="Validate a translated book against its source fixture.")
    parser.add_argument("format", choices=sorted(PARSERS))
    parser.add_argument("source", type=Path)
    parser.add_argument("translated", type=Path)
    parser.add_argument("--lang", default="uk", help="target language tag (default uk); 'none' skips language checks")
    args = parser.parse_args(argv)
    lang = None if args.lang.lower() == "none" else args.lang.lower()
    results = validate(args.format, args.source, args.translated, lang)
    return 0 if overall(results) == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
