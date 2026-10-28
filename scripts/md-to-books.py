#!/usr/bin/env python3
"""Convert one GitHub-flavoured Markdown article into an EPUB 3 and a FictionBook 2.1 file.

    scripts/md-to-books.py SRC.md --lang uk --title T --author A --out-dir DIR [--formats epub,fb2] [--date 2000-01-01]

Outputs are DIR/<srcstem>.epub and DIR/<srcstem>.fb2. Pure standard library, no network: a remote image is never
fetched, a local one (./file.jpg, relative to SRC.md) is embedded.

Pipeline: Markdown -> a small block/inline tree (parse_blocks / parse_inline) -> one renderer per format.

Format choices
--------------
Reproducibility: the book id is a uuid5 of (lang, title, author, source text); every timestamp (zip entries,
dcterms:modified, FB2 dates) comes from --date (default 2000-01-01T00:00:00Z); entries are written in a fixed order
with fixed attributes, so two runs give byte-identical files.

EPUB 3: `mimetype` is the first entry and STORED; META-INF/container.xml points at OEBPS/content.opf; nav.xhtml holds
the TOC built from the headings (nested by level); the text before the first H2 (title, summary) is one chapter file
and every H2 section is one chapter file (OEBPS/text/chapter-NNN.xhtml); images live in OEBPS/images with manifest
media types. A remote image stays an external <img src> with its alt text and the chapter gets the
`remote-resources` manifest property. Code is <pre><code class="language-x">; a ```mermaid fence and a $$ block are
kept verbatim as code (language-mermaid / language-math). A GitHub alert (> [!NOTE]) is a blockquote with a class
and an English title; a task-list box is a character (checked / unchecked ballot box), not a form control;
<details> stays <details>; HTML comments are dropped. Heading ids are GitHub-style slugs so `#slug` links work.

FB2: nested <section>s follow the heading levels (the first H1 is the body title); a section never mixes loose
blocks with subsections (the loose blocks go into an untitled first subsection), as the schema demands. FB2 has no
lists, so an item is a <p> starting with a bullet, "N." or a ballot box, nested items are indented with no-break
spaces. A code block is <empty-line/>, one <p><code> per line (spaces kept as typed), <empty-line/>. A blockquote is
<cite>; an alert is a <cite> whose first paragraph is bold; a table is the FB2.1 <table>; <details> becomes a bold
summary paragraph followed by its content. A local image becomes a <binary> part and <image l:href="#name"/>; a
remote image becomes its alt text linked to the URL. The summary blockquote before the first H2 also feeds
<annotation> (and stays in the body so no text is lost).
"""

from __future__ import annotations

import argparse
import base64
import html
import io
import re
import string
import sys
import uuid
import zipfile
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote
from xml.sax.saxutils import escape as _xml_escape

DEFAULT_DATE = "2000-01-01T00:00:00Z"
NBSP = "\u00a0"
ILLEGAL_XML = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f\ufffe\uffff]")


# --------------------------------------------------------------------------------------------- tree

@dataclass(frozen=True)
class Inline:
    """kind: text code em strong del sup sub kbd mark link img br group."""
    kind: str
    text: str = ""          # text / code content, image alt
    children: tuple = ()
    href: str = ""          # link target or image source
    title: str = ""


@dataclass(frozen=True)
class ListItem:
    checked: bool | None
    blocks: tuple


@dataclass(frozen=True)
class Block:
    """kind: heading para code table list quote alert rule details."""
    kind: str
    level: int = 0           # heading level, or the first number of an ordered list
    inlines: tuple = ()      # heading / paragraph / details summary
    text: str = ""           # code body, alert kind
    lang: str = ""
    children: tuple = ()     # quote / alert / details content
    items: tuple = ()
    ordered: bool = False
    header: tuple = ()       # table: tuple of cells, a cell is a tuple of Inline
    rows: tuple = ()
    aligns: tuple = ()
    slug: str = ""           # heading anchor id (top-level headings only)


def plain(nodes) -> str:
    """Plain text of inline nodes (alt text, titles, annotation)."""
    parts = []
    for n in nodes:
        if n.kind in ("text", "code"):
            parts.append(n.text)
        elif n.kind == "br":
            parts.append(" ")
        elif n.kind == "img":
            parts.append(n.text)
        else:
            parts.append(plain(n.children))
    return "".join(parts)


# ----------------------------------------------------------------------------------------- inlines

PUNCT = set(string.punctuation)
TAG_RE = re.compile(
    r'<(/?)([A-Za-z][A-Za-z0-9-]*)((?:\s+[A-Za-z_:][\w:.-]*(?:\s*=\s*(?:"[^"]*"|\'[^\']*\'|[^\s"\'=<>`]+))?)*)\s*(/?)>')
ATTR_RE = re.compile(r'([\w:.-]+)\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s"\'=<>`]+))')
AUTOLINK_RE = re.compile(r"<((?:https?://|mailto:)[^\s<>]+|[^\s@<>]+@[^\s@<>]+)>")
ENTITY_RE = re.compile(r"&(#\d+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")
HARD_BREAK_RE = re.compile(r" {2,}\n")
PAIRED_HTML = {
    "sup": "sup", "sub": "sub", "kbd": "kbd", "mark": "mark", "b": "strong", "strong": "strong", "i": "em",
    "em": "em", "s": "del", "strike": "del", "del": "del", "code": "code", "a": "link",
}


def text_node(s: str) -> Inline:
    return Inline("text", text=s)


def normalize_label(label: str) -> str:
    return " ".join(label.lower().split())


def parse_attrs(raw: str) -> dict[str, str]:
    return {m.group(1).lower(): html.unescape(m.group(2) or m.group(3) or m.group(4) or "")
            for m in ATTR_RE.finditer(raw)}


def skip_code_span(s: str, j: int) -> int:
    """Index after the code span opening at s[j], or after the backtick run if it never closes."""
    k = j
    while k < len(s) and s[k] == "`":
        k += 1
    close = re.compile("(?<!`)" + "`" * (k - j) + "(?!`)").search(s, k)
    return close.end() if close else k


def find_close_bracket(s: str, i: int) -> int:
    depth, j = 0, i
    while j < len(s):
        c = s[j]
        if c == "\\":
            j += 2
            continue
        if c == "`":
            j = skip_code_span(s, j)
            continue
        depth += (c == "[") - (c == "]")
        if depth == 0:
            return j
        j += 1
    return -1


def read_destination(s: str, p: int) -> tuple[str, int] | None:
    if p < len(s) and s[p] == "<":
        end = s.find(">", p)
        return (s[p + 1:end], end + 1) if end != -1 else None
    depth, q = 0, p
    while q < len(s) and not s[q].isspace():
        if s[q] == "\\":
            q += 2
            continue
        if s[q] == "(":
            depth += 1
        elif s[q] == ")":
            if depth == 0:
                break
            depth -= 1
        q += 1
    return s[p:q], q


def read_title(s: str, p: int) -> tuple[str, int]:
    if p < len(s) and s[p] in "\"'(":
        closer = ")" if s[p] == "(" else s[p]
        end = s.find(closer, p + 1)
        if end != -1:
            return s[p + 1:end], end + 1
    return "", p


def skip_ws(s: str, p: int) -> int:
    while p < len(s) and s[p].isspace():
        p += 1
    return p


def unescape_target(s: str) -> str:
    return html.unescape(re.sub(r"\\([!-/:-@\[-`{-~])", r"\1", s))


class InlineParser:
    def __init__(self, refs: dict[str, tuple[str, str]]):
        self.refs = refs
        self.handlers = {
            "\\": self._escape, "`": self._code, "!": self._image, "[": self._link, "<": self._angle,
            "*": self._emph, "_": self._emph, "~": self._emph, " ": self._hard_break, "&": self._entity,
        }

    def parse(self, s: str) -> tuple[Inline, ...]:
        out: list[Inline] = []
        buf: list[str] = []
        i = 0
        while i < len(s):
            handler = self.handlers.get(s[i])
            result = handler(s, i) if handler else None
            if result is None:
                buf.append(s[i])
                i += 1
                continue
            nodes, i = result
            if buf:
                out.append(text_node("".join(buf)))
                buf = []
            out.extend(nodes)
        if buf:
            out.append(text_node("".join(buf)))
        return tuple(out)

    def _escape(self, s, i):
        nxt = s[i + 1] if i + 1 < len(s) else ""
        if nxt == "\n":
            return [Inline("br")], i + 2
        if nxt in PUNCT and nxt:
            return [text_node(nxt)], i + 2
        return None

    def _hard_break(self, s, i):
        m = HARD_BREAK_RE.match(s, i)
        return ([Inline("br")], m.end()) if m else None

    def _entity(self, s, i):
        m = ENTITY_RE.match(s, i)
        if not m:
            return None
        decoded = html.unescape(m.group(0))
        return ([text_node(decoded)], m.end()) if decoded != m.group(0) else None

    def _code(self, s, i):
        k = i
        while k < len(s) and s[k] == "`":
            k += 1
        run = k - i
        close = re.compile("(?<!`)" + "`" * run + "(?!`)").search(s, k)
        if not close:
            return [text_node("`" * run)], k
        content = s[k:close.start()].replace("\n", " ")
        if len(content) > 2 and content[0] == " " and content[-1] == " " and content.strip():
            content = content[1:-1]
        return [Inline("code", text=content)], close.end()

    def _link_target(self, s, j, label):
        """Parse what follows the `]` at s[j]: (url, title, end) or None."""
        nxt = s[j + 1] if j + 1 < len(s) else ""
        if nxt == "(":
            dest = read_destination(s, skip_ws(s, j + 2))
            if dest is None:
                return None
            p = skip_ws(s, dest[1])
            title, p = read_title(s, p)
            p = skip_ws(s, p)
            if p < len(s) and s[p] == ")":
                return unescape_target(dest[0]), title, p + 1
            return None
        if nxt == "[":
            end = s.find("]", j + 2)
            if end == -1:
                return None
            key, stop = (s[j + 2:end] or label), end + 1
        else:
            key, stop = label, j + 1
        ref = self.refs.get(normalize_label(key))
        return (ref[0], ref[1], stop) if ref else None

    def _link(self, s, i):
        j = find_close_bracket(s, i)
        if j == -1:
            return None
        label = s[i + 1:j]
        target = self._link_target(s, j, label)
        if target is None:
            return None
        url, title, end = target
        return [Inline("link", children=self.parse(label), href=url, title=title)], end

    def _image(self, s, i):
        if i + 1 >= len(s) or s[i + 1] != "[":
            return None
        j = find_close_bracket(s, i + 1)
        if j == -1:
            return None
        label = s[i + 2:j]
        target = self._link_target(s, j, label)
        if target is None:
            return None
        url, title, end = target
        return [Inline("img", text=plain(self.parse(label)), href=url, title=title)], end

    def _angle(self, s, i):
        if s.startswith("<!--", i):
            end = s.find("-->", i + 4)
            return ([], end + 3) if end != -1 else None
        auto = AUTOLINK_RE.match(s, i)
        if auto:
            url = auto.group(1)
            href = url if re.match(r"(https?|mailto):", url) else "mailto:" + url
            return [Inline("link", children=(text_node(url),), href=href)], auto.end()
        tag = TAG_RE.match(s, i)
        return self._html_tag(s, tag) if tag else None

    def _html_tag(self, s, m):
        closing, name, raw_attrs, _ = m.group(1), m.group(2).lower(), m.group(3), m.group(4)
        attrs = parse_attrs(raw_attrs)
        if closing:
            return [], m.end()
        if name == "br":
            return [Inline("br")], m.end()
        if name == "img":
            return [Inline("img", text=attrs.get("alt", ""), href=attrs.get("src", ""),
                           title=attrs.get("title", ""))], m.end()
        closer = re.compile(r"</%s\s*>" % re.escape(name), re.I).search(s, m.end())
        if not closer or m.group(0).endswith("/>"):
            return [], m.end()
        children = self.parse(s[m.end():closer.start()])
        return [self._paired(name, attrs, children)], closer.end()

    def _paired(self, name, attrs, children) -> Inline:
        kind = PAIRED_HTML.get(name, "group")
        if kind == "code":
            return Inline("code", text=plain(children))
        if kind == "link":
            return Inline("link", children=children, href=attrs["href"], title=attrs.get("title", "")) \
                if "href" in attrs else Inline("group", children=children)
        return Inline(kind, children=children)

    def _emph(self, s, i):
        c = s[i]
        run = 1
        while i + run < len(s) and s[i + run] == c:
            run += 1
        after = s[i + run] if i + run < len(s) else ""
        before = s[i - 1] if i > 0 else ""
        if not after or after.isspace() or run > 3 or (c == "~" and run != 2):
            return None
        if c == "_" and before.isalnum():
            return None
        for length in ([2] if c == "~" else range(run, 0, -1)):
            close = find_closer(s, i + run, c, length)
            if close != -1:
                inner = self.parse(s[i + run:close])
                prefix = [text_node(c * (run - length))] if run > length else []
                return prefix + [wrap_emphasis(c, length, inner)], close + length
        return None


def wrap_emphasis(c: str, length: int, inner) -> Inline:
    if c == "~":
        return Inline("del", children=inner)
    if length == 1:
        return Inline("em", children=inner)
    if length == 2:
        return Inline("strong", children=inner)
    return Inline("em", children=(Inline("strong", children=inner),))


def find_closer(s: str, start: int, c: str, length: int) -> int:
    """Index of the closing delimiter run of `length` chars of `c`, or -1."""
    j = start
    while j < len(s):
        ch = s[j]
        if ch == "\\":
            j += 2
        elif ch == "`":
            j = skip_code_span(s, j)
        elif ch == c:
            run = 1
            while j + run < len(s) and s[j + run] == c:
                run += 1
            pos = closer_position(s, j, run, c, length, start)
            if pos != -1:
                return pos
            j += run
        else:
            j += 1
    return -1


def closer_position(s, j, run, c, length, start) -> int:
    if j == start or s[j - 1].isspace():
        return -1
    if c == "_" and j + run < len(s) and s[j + run].isalnum():
        return -1
    if run == length:
        return j
    if run == 3 and length < 3:
        return j + run - length
    return -1


# ------------------------------------------------------------------------------------------- blocks

FENCE_RE = re.compile(r"^( {0,3})(`{3,}|~{3,})\s*([^`\s]*)[^`]*$")
ATX_RE = re.compile(r"^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$")
HR_RE = re.compile(r"^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$")
QUOTE_RE = re.compile(r"^ {0,3}>")
LIST_RE = re.compile(r"^( {0,3})([-+*]|\d{1,9}[.)])(?:([ \t]+)(.*)|[ \t]*)$")
SETEXT_RE = re.compile(r"^ {0,3}(=+|-+)[ \t]*$")
TABLE_DELIM_RE = re.compile(r"^[ \t]*\|?[ \t]*:?-+:?[ \t]*(?:\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$")
HTML_BLOCK_RE = re.compile(r"^(?:</?(?:details|summary)\b|<!--)", re.I)
ALERT_RE = re.compile(r"^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\][ \t]*$", re.I)
TASK_RE = re.compile(r"^\[([ xX])\](?:[ \t]+|$)")
REF_DEF_RE = re.compile(r"^ {0,3}\[([^\]^][^\]]*)\]:[ \t]*<?(\S+?)>?(?:[ \t]+[\"'(](.*)[\"')])?[ \t]*$")


def indent_width(line: str) -> int:
    width = 0
    for ch in line:
        if ch == " ":
            width += 1
        elif ch == "\t":
            width += 4 - width % 4
        else:
            break
    return width


def lstrip_columns(line: str, n: int) -> str:
    """Remove up to n columns of leading whitespace (a tab counts to the next multiple of 4)."""
    width, k = 0, 0
    while k < len(line) and width < n:
        if line[k] == " ":
            width += 1
        elif line[k] == "\t":
            step = 4 - width % 4
            if width + step > n:
                return " " * (width + step - n) + line[k + 1:]
            width += step
        else:
            break
        k += 1
    return line[k:]


def starts_block(line: str) -> bool:
    """True when this line may interrupt a paragraph."""
    if FENCE_RE.match(line) or ATX_RE.match(line) or HR_RE.match(line) or QUOTE_RE.match(line):
        return True
    stripped = line.strip()
    if stripped.startswith("$$") or HTML_BLOCK_RE.match(stripped):
        return True
    m = LIST_RE.match(line)
    return bool(m and m.group(4) and (not m.group(2)[0].isdigit() or m.group(2)[:-1] == "1"))


def split_row(line: str) -> list[str]:
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|") and not s.endswith("\\|"):
        s = s[:-1]
    return [c.strip().replace("\\|", "|") for c in re.split(r"(?<!\\)\|", s)]


def table_aligns(delim: str) -> tuple[str, ...]:
    out = []
    for cell in split_row(delim):
        left, right = cell.startswith(":"), cell.endswith(":")
        out.append("center" if left and right else "left" if left else "right" if right else "")
    return tuple(out)


class BlockParser:
    def __init__(self, refs: dict[str, tuple[str, str]]):
        self.inline = InlineParser(refs)
        self.handlers = (self._fence, self._math, self._comment, self._details, self._atx, self._hr,
                         self._quote, self._list, self._table, self._indented, self._paragraph)

    def parse(self, lines: list[str]) -> list[Block]:
        blocks: list[Block] = []
        i = 0
        while i < len(lines):
            if not lines[i].strip():
                i += 1
                continue
            for handler in self.handlers:
                result = handler(lines, i)
                if result is not None:
                    block, i = result
                    if block is not None:
                        blocks.append(block)
                    break
        return blocks

    def _fence(self, lines, i):
        m = FENCE_RE.match(lines[i])
        if not m:
            return None
        indent, fence, lang = len(m.group(1)), m.group(2), m.group(3)
        close = re.compile(r"^ {0,3}" + re.escape(fence[0]) + "{%d,}[ \t]*$" % len(fence))
        j, body = i + 1, []
        while j < len(lines) and not close.match(lines[j]):
            body.append(lstrip_columns(lines[j], indent))
            j += 1
        return Block("code", text="\n".join(body), lang=lang), j + 1

    def _math(self, lines, i):
        first = lines[i].strip()
        if not first.startswith("$$"):
            return None
        j = i
        if not (len(first) > 2 and first.endswith("$$")):
            j = i + 1
            while j < len(lines) and not lines[j].rstrip().endswith("$$"):
                j += 1
        body = [lines[k].strip() for k in range(i, min(j, len(lines) - 1) + 1)]
        return Block("code", text="\n".join(body), lang="math"), j + 1

    def _comment(self, lines, i):
        if not lines[i].strip().startswith("<!--"):
            return None
        j = i
        while j < len(lines) and "-->" not in lines[j]:
            j += 1
        return None, j + 1

    def _details(self, lines, i):
        if not re.match(r"^<details\b", lines[i].strip(), re.I):
            return None
        depth, j = 0, i
        while j < len(lines):
            depth += len(re.findall(r"<details\b", lines[j], re.I)) - len(re.findall(r"</details\s*>", lines[j], re.I))
            if depth <= 0:
                break
            j += 1
        raw = "\n".join(lines[i:j + 1])
        raw = re.sub(r"^\s*<details\b[^>]*>", "", raw, count=1, flags=re.I)
        raw = re.sub(r"</details\s*>\s*$", "", raw, flags=re.I)
        summary = re.search(r"<summary\b[^>]*>(.*?)</summary\s*>", raw, re.I | re.S)
        inlines = self.inline.parse(" ".join(summary.group(1).split())) if summary else ()
        if summary:
            raw = raw[:summary.start()] + raw[summary.end():]
        return Block("details", inlines=inlines, children=tuple(self.parse(raw.split("\n")))), j + 1

    def _atx(self, lines, i):
        m = ATX_RE.match(lines[i])
        if not m:
            return None
        return Block("heading", level=len(m.group(1)), inlines=self.inline.parse((m.group(2) or "").strip())), i + 1

    def _hr(self, lines, i):
        return (Block("rule"), i + 1) if HR_RE.match(lines[i]) else None

    def _quote(self, lines, i):
        if not QUOTE_RE.match(lines[i]):
            return None
        j, inner = i, []
        while j < len(lines) and QUOTE_RE.match(lines[j]):
            inner.append(re.sub(r"^ {0,3}> ?", "", lines[j]))
            j += 1
        alert = ALERT_RE.match(inner[0].strip())
        if alert:
            children = tuple(self.parse(inner[1:]))
            return Block("alert", text=alert.group(1).upper(), children=children), j
        return Block("quote", children=tuple(self.parse(inner))), j

    def _list(self, lines, i):
        m = LIST_RE.match(lines[i])
        if not m:
            return None
        marker = m.group(2)
        ordered = marker[0].isdigit()
        kind = marker[-1] if ordered else marker
        start = int(marker[:-1]) if ordered else 1
        items, j = [], i
        while j < len(lines):
            item, j = self._list_item(lines, j)
            items.append(item)
            nxt = j
            while nxt < len(lines) and not lines[nxt].strip():
                nxt += 1
            nm = LIST_RE.match(lines[nxt]) if nxt < len(lines) else None
            if not nm or (nm.group(2)[-1] if nm.group(2)[0].isdigit() else nm.group(2)) != kind \
                    or nm.group(2)[0].isdigit() != ordered or HR_RE.match(lines[nxt]):
                break
            j = nxt
        return Block("list", level=start, ordered=ordered, items=tuple(items)), j

    def _list_item(self, lines, j):
        m = LIST_RE.match(lines[j])
        marker, spaces = m.group(2), len(m.group(3) or "")
        pad = 1 if m.group(4) is None or spaces > 4 else spaces
        offset = len(m.group(1)) + len(marker) + pad
        first = (" " * (spaces - 1) if spaces > 4 else "") + (m.group(4) or "")
        content, k = [first], j + 1
        while k < len(lines):
            line = lines[k]
            if not line.strip():
                nxt = k
                while nxt < len(lines) and not lines[nxt].strip():
                    nxt += 1
                if nxt < len(lines) and indent_width(lines[nxt]) >= offset:
                    content.extend([""] * (nxt - k))
                    k = nxt
                    continue
                break
            if indent_width(line) >= offset:
                content.append(lstrip_columns(line, offset))
            elif content[-1].strip() and not starts_block(line) and not LIST_RE.match(line):
                content.append(line.lstrip())
            else:
                break
            k += 1
        checked = None
        task = TASK_RE.match(content[0])
        if task:
            checked = task.group(1) in "xX"
            content[0] = content[0][task.end():]
        return ListItem(checked, tuple(self.parse(content))), k

    def _table(self, lines, i):
        if "|" not in lines[i] or i + 1 >= len(lines):
            return None
        delim = lines[i + 1]
        if "|" not in delim or not TABLE_DELIM_RE.match(delim):
            return None
        header = split_row(lines[i])
        if len(header) != len(split_row(delim)):
            return None
        rows, j = [], i + 2
        while j < len(lines) and lines[j].strip() and not starts_block(lines[j]):
            cells = split_row(lines[j])[:len(header)]
            cells += [""] * (len(header) - len(cells))
            rows.append(tuple(self.inline.parse(c) for c in cells))
            j += 1
        block = Block("table", header=tuple(self.inline.parse(c) for c in header), rows=tuple(rows),
                      aligns=table_aligns(delim))
        return block, j

    def _indented(self, lines, i):
        if indent_width(lines[i]) < 4:
            return None
        j, body = i, []
        while j < len(lines) and (not lines[j].strip() or indent_width(lines[j]) >= 4):
            body.append(lstrip_columns(lines[j], 4))
            j += 1
        while body and not body[-1].strip():
            body.pop()
        return Block("code", text="\n".join(body)), j

    def _paragraph(self, lines, i):
        j = i + 1
        while j < len(lines) and lines[j].strip():
            setext = SETEXT_RE.match(lines[j])
            if setext:
                level = 1 if setext.group(1)[0] == "=" else 2
                text = " ".join(x.strip() for x in lines[i:j])
                return Block("heading", level=level, inlines=self.inline.parse(text)), j + 1
            if starts_block(lines[j]):
                break
            j += 1
        text = "\n".join(x.lstrip() for x in lines[i:j]).rstrip()
        inlines = self.inline.parse(text)
        if not plain(inlines).strip() and not any(n.kind == "img" for n in inlines):
            return None, j
        return Block("para", inlines=inlines), j


def extract_references(lines: list[str]) -> tuple[list[str], dict[str, tuple[str, str]]]:
    """Remove `[label]: url "title"` definitions outside fences; return the rest and the table."""
    refs: dict[str, tuple[str, str]] = {}
    kept: list[str] = []
    fence = None
    for line in lines:
        fm = FENCE_RE.match(line)
        if fence is None and fm:
            fence = fm.group(2)
        elif fence is not None and fm and fm.group(2)[0] == fence[0] and len(fm.group(2)) >= len(fence) \
                and not fm.group(3):
            fence = None
        elif fence is None:
            rm = REF_DEF_RE.match(line)
            if rm:
                refs.setdefault(normalize_label(rm.group(1)), (unescape_target(rm.group(2)), rm.group(3) or ""))
                continue
        kept.append(line)
    return kept, refs


def github_slug(text: str) -> str:
    slug = re.sub(r"[^\w\- ]", "", text.lower())
    return slug.replace(" ", "-")


def anchor_id(slug: str) -> str:
    """A slug that is a valid XML name (ids may not start with a digit or hyphen)."""
    return slug if slug and (slug[0].isalpha() or slug[0] == "_") else "s-" + slug


def number_headings(blocks: list[Block]) -> list[Block]:
    """Give every top-level heading a unique GitHub-style anchor."""
    seen: dict[str, int] = {}
    out = []
    for b in blocks:
        if b.kind == "heading":
            base = github_slug(plain(b.inlines)) or "section"
            count = seen.get(base, 0)
            seen[base] = count + 1
            b = replace(b, slug=base if count == 0 else f"{base}-{count}")
        out.append(b)
    return out


# ------------------------------------------------------------------------------------------ document

@dataclass(frozen=True)
class Document:
    blocks: tuple
    title: str
    author: str
    lang: str
    stamp: datetime
    uid: str
    src_dir: Path
    annotation: str = ""


def clean_text(s: str) -> str:
    s = s.lstrip("\ufeff").replace("\r\n", "\n").replace("\r", "\n")
    return ILLEGAL_XML.sub("", s)


def parse_stamp(value: str) -> datetime:
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return (dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)).astimezone(timezone.utc)


def find_annotation(blocks) -> str:
    for b in blocks:
        if b.kind == "heading" and b.level == 2:
            return ""
        if b.kind == "quote":
            text = " ".join(plain(c.inlines) for c in b.children if c.kind == "para").strip()
            return re.sub(r"^(summary|annotation|abstract)\s*:\s*", "", text, flags=re.I)
    return ""


def build_document(source: str, src_dir: Path, stem: str, title: str | None, author: str, lang: str,
                   stamp: datetime) -> Document:
    text = clean_text(source)
    lines, refs = extract_references(text.split("\n"))
    blocks = number_headings(BlockParser(refs).parse(lines))
    if not title:
        first = next((b for b in blocks if b.kind == "heading" and b.level == 1), None)
        title = plain(first.inlines) if first else stem
    uid = str(uuid.uuid5(uuid.NAMESPACE_OID, f"{lang}\n{title}\n{author}\n{text}"))
    return Document(tuple(blocks), title, author, lang, stamp, uid, src_dir, find_annotation(blocks))


# ------------------------------------------------------------------------------------------- images

MEDIA_TYPES = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".gif": "image/gif",
               ".svg": "image/svg+xml", ".webp": "image/webp"}


@dataclass(frozen=True)
class Asset:
    name: str
    path: Path
    media_type: str


def is_remote(src: str) -> bool:
    return bool(re.match(r"https?://", src, re.I))


class ImageStore:
    """Resolves local image sources to files and gives each a unique name; remote ones are never fetched."""

    def __init__(self, src_dir: Path):
        self.src_dir = src_dir
        self.assets: dict[Path, Asset] = {}
        self.names: set[str] = set()
        self.warned: set[str] = set()

    def resolve(self, src: str) -> Asset | None:
        if not src or is_remote(src) or re.match(r"[a-z][a-z0-9+.-]*:", src, re.I):
            return None
        path = (self.src_dir / unquote(re.split(r"[?#]", src)[0])).resolve()
        media = MEDIA_TYPES.get(path.suffix.lower())
        if not path.is_file() or media is None:
            if src not in self.warned:
                self.warned.add(src)
                print(f"warning: image not embedded (missing or unsupported): {src}", file=sys.stderr)
            return None
        if path not in self.assets:
            self.assets[path] = Asset(self._unique(path.name), path, media)
        return self.assets[path]

    def _unique(self, filename: str) -> str:
        safe = re.sub(r"[^\w.-]", "_", filename)
        stem, dot, ext = safe.rpartition(".")
        name, n = safe, 1
        while name in self.names:
            n += 1
            name = f"{stem}-{n}{dot}{ext}"
        self.names.add(name)
        return name


def esc(s: str) -> str:
    return _xml_escape(ILLEGAL_XML.sub("", s))


def attr(s: str) -> str:
    return _xml_escape(ILLEGAL_XML.sub("", s), {'"': "&quot;"})


def safe_lang(lang: str) -> str:
    return re.sub(r"[^A-Za-z0-9_+.-]", "", lang)


def lone_image(block: Block) -> Inline | None:
    """The image if the paragraph holds nothing else."""
    nodes = [n for n in block.inlines if not (n.kind == "text" and not n.text.strip())]
    if len(nodes) == 1 and nodes[0].kind == "img":
        return nodes[0]
    return None


# --------------------------------------------------------------------------------------------- EPUB

ALERT_TITLES = {"NOTE": "Note", "TIP": "Tip", "IMPORTANT": "Important", "WARNING": "Warning",
                "CAUTION": "Caution"}
TOC_TITLES = {"uk": "Зміст"}
CSS = """\
body { font-family: serif; line-height: 1.4; margin: 1em; }
h1, h2, h3, h4, h5, h6 { font-family: sans-serif; line-height: 1.2; }
pre { background: #f4f4f4; padding: 0.6em; overflow-x: auto; white-space: pre-wrap; }
code, kbd { font-family: monospace; }
table { border-collapse: collapse; margin: 1em 0; }
th, td { border: 1px solid #999; padding: 0.3em 0.6em; }
.align-left { text-align: left; }
.align-center { text-align: center; }
.align-right { text-align: right; }
blockquote { margin: 1em 0 1em 1em; padding-left: 1em; border-left: 3px solid #bbb; }
blockquote.alert { border-left-color: #4a7bd0; }
.alert-title { font-weight: bold; margin: 0 0 0.3em 0; }
img { max-width: 100%; }
p.image { text-align: center; text-indent: 0; }
.task-box { font-family: sans-serif; }
details { margin: 1em 0; }
"""


@dataclass
class Chapter:
    title: str
    blocks: list
    file: str = ""


@dataclass
class NavEntry:
    level: int
    title: str
    href: str
    children: list = field(default_factory=list)


def split_chapters(doc: Document) -> list[Chapter]:
    """The text before the first H2 is one chapter; every H2 section is another."""
    chapters: list[Chapter] = []
    for b in doc.blocks:
        if b.kind == "heading" and b.level == 2:
            chapters.append(Chapter(plain(b.inlines), [b]))
        elif chapters:
            chapters[-1].blocks.append(b)
        else:
            chapters.append(Chapter(doc.title, [b]))
    for n, chapter in enumerate(chapters, 1):
        chapter.file = f"chapter-{n:03d}.xhtml"
    return chapters or [Chapter(doc.title, [], "chapter-001.xhtml")]


class EpubRenderer:
    def __init__(self, doc: Document, images: ImageStore, anchors: dict[str, str]):
        self.doc, self.images, self.anchors = doc, images, anchors
        self.remote = False

    def inline(self, nodes) -> str:
        return "".join(self._node(n) for n in nodes)

    def _node(self, n: Inline) -> str:
        simple = {"em": "em", "strong": "strong", "del": "del", "sup": "sup", "sub": "sub", "kbd": "kbd",
                  "mark": "mark"}
        if n.kind == "text":
            return esc(n.text)
        if n.kind == "code":
            return f"<code>{esc(n.text)}</code>"
        if n.kind in simple:
            return f"<{simple[n.kind]}>{self.inline(n.children)}</{simple[n.kind]}>"
        if n.kind == "br":
            return "<br/>"
        if n.kind == "group":
            return self.inline(n.children)
        if n.kind == "link":
            title = f' title="{attr(n.title)}"' if n.title else ""
            return f'<a href="{attr(self._href(n.href))}"{title}>{self.inline(n.children)}</a>'
        return self._image(n)

    def _href(self, href: str) -> str:
        if href.startswith("#") and href[1:] in self.anchors:
            return self.anchors[href[1:]]
        return href

    def _image(self, n: Inline) -> str:
        title = f' title="{attr(n.title)}"' if n.title else ""
        asset = self.images.resolve(n.href)
        if asset:
            return f'<img src="../images/{attr(asset.name)}" alt="{attr(n.text)}"{title}/>'
        if is_remote(n.href):
            self.remote = True
            return f'<img src="{attr(n.href)}" alt="{attr(n.text)}"{title}/>'
        return f'<span class="missing-image">{esc(n.text)}</span>'

    def blocks(self, blocks) -> str:
        return "\n".join(self.block(b) for b in blocks)

    def block(self, b: Block) -> str:
        method = getattr(self, "_b_" + b.kind)
        return method(b)

    def _b_heading(self, b):
        ident = f' id="{attr(anchor_id(b.slug))}"' if b.slug else ""
        level = min(b.level, 6)
        return f"<h{level}{ident}>{self.inline(b.inlines)}</h{level}>"

    def _b_para(self, b):
        css = ' class="image"' if lone_image(b) else ""
        return f"<p{css}>{self.inline(b.inlines)}</p>"

    def _b_code(self, b):
        lang = safe_lang(b.lang)
        css = f' class="language-{lang}"' if lang else ""
        return f"<pre><code{css}>{esc(b.text)}</code></pre>"

    def _b_rule(self, b):
        return "<hr/>"

    def _b_quote(self, b):
        return f"<blockquote>\n{self.blocks(b.children)}\n</blockquote>"

    def _b_alert(self, b):
        title = ALERT_TITLES[b.text]
        return (f'<blockquote class="alert alert-{b.text.lower()}">\n<p class="alert-title">{title}</p>\n'
                f"{self.blocks(b.children)}\n</blockquote>")

    def _b_details(self, b):
        summary = f"<summary>{self.inline(b.inlines)}</summary>\n" if b.inlines else ""
        return f"<details>\n{summary}{self.blocks(b.children)}\n</details>"

    def _b_table(self, b):
        def cell(tag, nodes, align):
            css = f' class="align-{align}"' if align else ""
            return f"<{tag}{css}>{self.inline(nodes)}</{tag}>"

        head = "".join(cell("th", c, a) for c, a in zip(b.header, b.aligns))
        body = "\n".join("<tr>" + "".join(cell("td", c, a) for c, a in zip(row, b.aligns)) + "</tr>"
                         for row in b.rows)
        return f"<table>\n<thead><tr>{head}</tr></thead>\n<tbody>\n{body}\n</tbody>\n</table>"

    def _b_list(self, b):
        tag = "ol" if b.ordered else "ul"
        start = f' start="{b.level}"' if b.ordered and b.level != 1 else ""
        items = "\n".join(self._item(i) for i in b.items)
        return f"<{tag}{start}>\n{items}\n</{tag}>"

    def _item(self, item: ListItem) -> str:
        box = ""
        if item.checked is not None:
            box = f'<span class="task-box">{"☑" if item.checked else "☐"}</span> '
        blocks = list(item.blocks)
        if len(blocks) == 1 and blocks[0].kind == "para":
            return f"<li>{box}{self.inline(blocks[0].inlines)}</li>"
        if blocks and blocks[0].kind == "para":
            head = f"{box}{self.inline(blocks.pop(0).inlines)}\n"
            return f"<li>{head}{self.blocks(blocks)}</li>"
        return f"<li>{box}{self.blocks(blocks)}</li>"


def xhtml_page(doc: Document, title: str, body: str) -> str:
    lang = attr(doc.lang)
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE html>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" '
            f'lang="{lang}" xml:lang="{lang}">\n<head>\n<meta charset="utf-8"/>\n<title>{esc(title)}</title>\n'
            '<link rel="stylesheet" type="text/css" href="../styles.css"/>\n</head>\n'
            f"<body>\n{body}\n</body>\n</html>\n")


def nav_tree(entries: list[tuple[int, str, str]]) -> list[NavEntry]:
    root = NavEntry(0, "", "")
    stack = [root]
    for level, title, href in entries:
        while stack[-1].level >= level:
            stack.pop()
        node = NavEntry(level, title, href)
        stack[-1].children.append(node)
        stack.append(node)
    return root.children


def nav_list(nodes: list[NavEntry]) -> str:
    items = []
    for n in nodes:
        sub = f"\n{nav_list(n.children)}" if n.children else ""
        items.append(f'<li><a href="{attr(n.href)}">{esc(n.title)}</a>{sub}</li>')
    return "<ol>\n" + "\n".join(items) + "\n</ol>"


def nav_page(doc: Document, entries) -> str:
    lang = attr(doc.lang)
    heading = TOC_TITLES.get(doc.lang.split("-")[0].lower(), "Contents")
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE html>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" '
            f'lang="{lang}" xml:lang="{lang}">\n<head>\n<meta charset="utf-8"/>\n<title>{esc(heading)}</title>\n'
            '<link rel="stylesheet" type="text/css" href="styles.css"/>\n</head>\n<body>\n'
            f'<nav epub:type="toc" id="toc">\n<h1>{esc(heading)}</h1>\n{nav_list(nav_tree(entries))}\n</nav>\n'
            "</body>\n</html>\n")


CONTAINER_XML = ('<?xml version="1.0" encoding="UTF-8"?>\n'
                 '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
                 '<rootfiles>\n<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>\n'
                 "</rootfiles>\n</container>\n")


def opf_package(doc: Document, chapters, remote_files: set[str], assets: list[Asset]) -> str:
    modified = doc.stamp.strftime("%Y-%m-%dT%H:%M:%SZ")
    description = f"<dc:description>{esc(doc.annotation)}</dc:description>\n" if doc.annotation else ""
    manifest = ['<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>',
                '<item id="css" href="styles.css" media-type="text/css"/>']
    for n, ch in enumerate(chapters, 1):
        props = ' properties="remote-resources"' if ch.file in remote_files else ""
        manifest.append(f'<item id="ch{n}" href="text/{ch.file}" media-type="application/xhtml+xml"{props}/>')
    for n, asset in enumerate(assets, 1):
        manifest.append(f'<item id="img{n}" href="images/{attr(asset.name)}" media-type="{asset.media_type}"/>')
    spine = "\n".join(f'<itemref idref="ch{n}"/>' for n in range(1, len(chapters) + 1))
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" '
            f'xml:lang="{attr(doc.lang)}">\n<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
            f'<dc:identifier id="book-id">urn:uuid:{doc.uid}</dc:identifier>\n<dc:title>{esc(doc.title)}</dc:title>\n'
            f"<dc:creator>{esc(doc.author)}</dc:creator>\n<dc:language>{esc(doc.lang)}</dc:language>\n"
            f'{description}<meta property="dcterms:modified">{modified}</meta>\n</metadata>\n'
            f"<manifest>\n" + "\n".join(manifest) + f"\n</manifest>\n<spine>\n{spine}\n</spine>\n</package>\n")


def build_epub(doc: Document) -> bytes:
    chapters = split_chapters(doc)
    anchors, headings = {}, []
    for ch in chapters:
        for b in ch.blocks:
            if b.kind == "heading" and b.slug:
                anchors[b.slug] = f"{ch.file}#{anchor_id(b.slug)}"
                headings.append((b.level, plain(b.inlines), f"text/{ch.file}#{anchor_id(b.slug)}"))
    if not headings:
        headings.append((1, doc.title, f"text/{chapters[0].file}"))
    images = ImageStore(doc.src_dir)
    files: list[tuple[str, bytes]] = []
    remote_files: set[str] = set()
    for ch in chapters:
        renderer = EpubRenderer(doc, images, anchors)
        body = renderer.blocks(ch.blocks)
        if renderer.remote:
            remote_files.add(ch.file)
        files.append((f"OEBPS/text/{ch.file}", xhtml_page(doc, ch.title, body).encode("utf-8")))
    assets = list(images.assets.values())
    files += [("OEBPS/nav.xhtml", nav_page(doc, headings).encode("utf-8")),
              ("OEBPS/styles.css", CSS.encode("utf-8")),
              ("OEBPS/content.opf", opf_package(doc, chapters, remote_files, assets).encode("utf-8"))]
    files += [(f"OEBPS/images/{a.name}", a.path.read_bytes()) for a in assets]
    buffer = io.BytesIO()
    write_epub_zip(buffer, [("META-INF/container.xml", CONTAINER_XML.encode("utf-8"))] + files, doc.stamp)
    return buffer.getvalue()


def zip_info(name: str, stamp: datetime, compress: int) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, (max(stamp.year, 1980), stamp.month, stamp.day, stamp.hour, stamp.minute,
                                  stamp.second - stamp.second % 2))
    info.compress_type = compress
    info.create_system = 3
    info.external_attr = 0o644 << 16
    return info


def write_epub_zip(target, files, stamp: datetime) -> None:
    with zipfile.ZipFile(target, "w") as z:
        z.writestr(zip_info("mimetype", stamp, zipfile.ZIP_STORED), b"application/epub+zip")
        for name, data in files:
            z.writestr(zip_info(name, stamp, zipfile.ZIP_DEFLATED), data, compresslevel=9)


# ---------------------------------------------------------------------------------------------- FB2

FB2_NS = "http://www.gribuser.ru/xml/fictionbook/2.0"
XLINK_NS = "http://www.w3.org/1999/xlink"


@dataclass
class Section:
    title: str
    anchor: str = ""
    blocks: list = field(default_factory=list)
    subs: list = field(default_factory=list)
    level: int = 0


def build_sections(doc: Document) -> tuple[str, list[Section]]:
    """Body title and the section tree; the first H1 is the body title, deeper headings nest."""
    title, intro, roots = doc.title, Section(""), []
    stack: list[Section] = []
    taken = False
    for b in doc.blocks:
        if b.kind != "heading":
            (stack[-1].blocks if stack else intro.blocks).append(b)
            continue
        if b.level == 1 and not taken and not stack and not intro.blocks:
            title, taken = plain(b.inlines), True
            continue
        while stack and stack[-1].level >= b.level:
            stack.pop()
        sec = Section(plain(b.inlines), anchor_id(b.slug), level=b.level)
        (stack[-1].subs if stack else roots).append(sec)
        stack.append(sec)
    if intro.blocks or not roots:
        roots.insert(0, intro)
    return title, roots


class Fb2Renderer:
    def __init__(self, doc: Document, images: ImageStore, anchors: set[str]):
        self.doc, self.images, self.anchors = doc, images, anchors
        self.in_cite = False

    # inline ---------------------------------------------------------------
    def inline(self, nodes) -> str:
        return "".join(self._node(n) for n in nodes)

    def _node(self, n: Inline) -> str:
        wrap = {"em": "emphasis", "strong": "strong", "del": "strikethrough", "sup": "sup", "sub": "sub",
                "kbd": "code"}
        if n.kind == "text":
            return esc(n.text.replace("\n", " "))
        if n.kind == "code":
            return f"<code>{esc(n.text)}</code>"
        if n.kind in wrap:
            return f"<{wrap[n.kind]}>{self.inline(n.children)}</{wrap[n.kind]}>"
        if n.kind == "br":
            return " "
        if n.kind == "link":
            return f'<a l:href="{attr(self._href(n.href))}">{self.inline(n.children)}</a>'
        if n.kind == "img":
            return self._image(n)
        return self.inline(n.children)

    def _href(self, href: str) -> str:
        if href.startswith("#") and anchor_id(href[1:]) in self.anchors:
            return "#" + anchor_id(href[1:])
        return href

    def _image(self, n: Inline) -> str:
        asset = self.images.resolve(n.href)
        if asset:
            return f'<image l:href="#{attr(asset.name)}"/>'
        if is_remote(n.href):
            return f'<a l:href="{attr(n.href)}">{esc(n.text or n.href)}</a>'
        return esc(n.text)

    # blocks ---------------------------------------------------------------
    def blocks(self, blocks, indent="", top=False) -> list[str]:
        out: list[str] = []
        for b in blocks:
            out.extend(getattr(self, "_b_" + b.kind)(b, indent, top))
        return out

    def _b_heading(self, b, indent, top):
        return [f"<subtitle>{esc(plain(b.inlines))}</subtitle>"]

    def _b_para(self, b, indent, top):
        image = lone_image(b)
        if image and top and not indent and self.images.resolve(image.href):
            asset = self.images.resolve(image.href)
            alt = f' alt="{attr(image.text)}"' if image.text else ""
            return [f'<image l:href="#{attr(asset.name)}"{alt}/>']
        content = self.inline(b.inlines).strip()
        return [f"<p>{indent}{content}</p>"] if content else []

    def _b_code(self, b, indent, top):
        lines = [f"<p><code>{esc(line)}</code></p>" if line else "<empty-line/>" for line in b.text.split("\n")]
        return ["<empty-line/>", *lines, "<empty-line/>"]

    def _b_rule(self, b, indent, top):
        return ["<subtitle>* * *</subtitle>"]

    def _b_quote(self, b, indent, top):
        if self.in_cite:
            return self.blocks(b.children, indent, False)
        return self._cite(self.blocks_in_cite(b.children, indent))

    def _b_alert(self, b, indent, top):
        head = [f"<p>{indent}<strong>{ALERT_TITLES[b.text]}</strong></p>"]
        return self._cite(head + self.blocks_in_cite(b.children, indent))

    def blocks_in_cite(self, children, indent) -> list[str]:
        previous, self.in_cite = self.in_cite, True
        try:
            return self.blocks(children, indent, False)
        finally:
            self.in_cite = previous

    @staticmethod
    def _cite(inner: list[str]) -> list[str]:
        return ["<cite>", *(inner or ["<empty-line/>"]), "</cite>"]

    def _b_details(self, b, indent, top):
        head = [f"<p>{indent}<strong>{self.inline(b.inlines)}</strong></p>"] if b.inlines else []
        return head + self.blocks(b.children, indent, False)

    def _b_table(self, b, indent, top):
        def cell(tag, nodes, align):
            a = f' align="{align}"' if align else ""
            return f"<{tag}{a}>{self.inline(nodes)}</{tag}>"

        rows = ["<tr>" + "".join(cell("th", c, a) for c, a in zip(b.header, b.aligns)) + "</tr>"]
        rows += ["<tr>" + "".join(cell("td", c, a) for c, a in zip(r, b.aligns)) + "</tr>" for r in b.rows]
        return ["<table>", *rows, "</table>"]

    def _b_list(self, b, indent, top):
        out, number = [], b.level
        for item in b.items:
            if item.checked is not None:
                marker = "☑ " if item.checked else "☐ "
            else:
                marker = f"{number}. " if b.ordered else "• "
            number += 1
            out.extend(self._item(item, marker, indent))
        return out

    def _item(self, item: ListItem, marker: str, indent: str) -> list[str]:
        blocks = list(item.blocks)
        if blocks and blocks[0].kind == "para":
            first = self.inline(blocks.pop(0).inlines).strip()
            head = f"<p>{indent}{marker}{first}</p>"
        else:
            head = f"<p>{indent}{marker.rstrip()}</p>"
        return [head, *self.blocks(blocks, indent + NBSP * 4, False)]

    # sections -------------------------------------------------------------
    def section(self, sec: Section) -> str:
        blocks, subs = sec.blocks, sec.subs
        if blocks and subs:
            subs, blocks = [Section("", "", blocks)] + subs, []
        ident = f' id="{attr(sec.anchor)}"' if sec.anchor else ""
        parts = [f"<section{ident}>"]
        if sec.title:
            parts.append(f"<title><p>{esc(sec.title)}</p></title>")
        parts += [self.section(s) for s in subs]
        parts += self.blocks(blocks, "", True)
        if not subs and not blocks:
            parts.append("<empty-line/>")
        parts.append("</section>")
        return "\n".join(parts)


def person_xml(name: str) -> str:
    parts = name.split()
    if len(parts) < 2:
        return f"<nickname>{esc(name)}</nickname>"
    return f"<first-name>{esc(' '.join(parts[:-1]))}</first-name><last-name>{esc(parts[-1])}</last-name>"


def binary_parts(assets: list[Asset]) -> str:
    out = []
    for a in assets:
        data = base64.b64encode(a.path.read_bytes()).decode("ascii")
        wrapped = "\n".join(data[i:i + 76] for i in range(0, len(data), 76))
        out.append(f'<binary id="{attr(a.name)}" content-type="{a.media_type}">\n{wrapped}\n</binary>')
    return "\n".join(out)


def fb2_description(doc: Document) -> str:
    day = doc.stamp.strftime("%Y-%m-%d")
    annotation = f"<annotation><p>{esc(doc.annotation)}</p></annotation>\n" if doc.annotation else ""
    return ("<description>\n<title-info>\n<genre>science</genre>\n"
            f"<author>{person_xml(doc.author)}</author>\n<book-title>{esc(doc.title)}</book-title>\n{annotation}"
            f'<date value="{day}">{day}</date>\n<lang>{esc(doc.lang)}</lang>\n</title-info>\n<document-info>\n'
            f"<author><nickname>md-to-books</nickname></author>\n<program-used>md-to-books.py</program-used>\n"
            f'<date value="{day}">{day}</date>\n<id>{doc.uid}</id>\n<version>1.0</version>\n'
            "</document-info>\n</description>\n")


def build_fb2(doc: Document) -> bytes:
    title, roots = build_sections(doc)
    anchors = {anchor_id(b.slug) for b in doc.blocks if b.kind == "heading" and b.slug}
    images = ImageStore(doc.src_dir)
    renderer = Fb2Renderer(doc, images, anchors)
    sections = "\n".join(renderer.section(s) for s in roots)
    binaries = binary_parts(list(images.assets.values()))
    text = ('<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<FictionBook xmlns="{FB2_NS}" xmlns:l="{XLINK_NS}">\n{fb2_description(doc)}'
            f"<body>\n<title><p>{esc(title)}</p></title>\n{sections}\n</body>\n"
            f"{binaries}{chr(10) if binaries else ''}</FictionBook>\n")
    return text.encode("utf-8")


# ----------------------------------------------------------------------------------------------- CLI

BUILDERS = {"epub": build_epub, "fb2": build_fb2}


def convert(src: Path, out_dir: Path, lang: str, title: str | None, author: str, formats: list[str],
            date: str = DEFAULT_DATE) -> list[Path]:
    doc = build_document(src.read_text(encoding="utf-8"), src.resolve().parent, src.stem, title, author, lang,
                         parse_stamp(date))
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for fmt in formats:
        target = out_dir / f"{src.stem}.{fmt}"
        target.write_bytes(BUILDERS[fmt](doc))
        written.append(target)
    return written


def parse_args(argv):
    p = argparse.ArgumentParser(description="Convert a Markdown article to EPUB 3 and FB2.")
    p.add_argument("src", type=Path)
    p.add_argument("--lang", default="en", help="BCP-47 language of the text (dc:language / FB2 lang)")
    p.add_argument("--title", help="book title (default: the first H1, else the file name)")
    p.add_argument("--author", default="Unknown", help="author name")
    p.add_argument("--out-dir", type=Path, required=True)
    p.add_argument("--formats", default="epub,fb2", help="comma list of: epub, fb2")
    p.add_argument("--date", default=DEFAULT_DATE, help="fixed timestamp for reproducible output (ISO 8601)")
    args = p.parse_args(argv)
    args.formats = [f.strip() for f in args.formats.split(",") if f.strip()]
    unknown = [f for f in args.formats if f not in BUILDERS]
    if unknown:
        p.error(f"unknown format: {', '.join(unknown)}")
    return args


def main(argv=None) -> int:
    args = parse_args(argv)
    for path in convert(args.src, args.out_dir, args.lang, args.title, args.author, args.formats, args.date):
        print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
