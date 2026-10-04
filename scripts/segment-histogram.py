#!/usr/bin/env python3
"""Read-only segment-length histogram: words per block of an EPUB, FB2, Markdown or TXT book.

Usage: scripts/segment-histogram.py <book> [<book> ...]

A block approximates a translation segment: an EPUB/XHTML paragraph, heading, list item or quote; an FB2 <p>, <v> or
<subtitle>; a Markdown paragraph, heading or list item; a TXT paragraph (text between blank lines). It reports the
number of blocks, the mean and median words per block, and for each length band the share of blocks and of words, so
the weight of the short blocks (many calls, few words) is visible. Nothing is written and no network is used.
"""

import re
import statistics
import sys
import zipfile
from html.parser import HTMLParser
from pathlib import Path

BANDS = [("1-3", 1, 3), ("4-10", 4, 10), ("11-30", 11, 30), (">30", 31, None)]
HTML_BLOCKS = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "dt", "dd", "figcaption"}
FB2_BLOCKS = {"p", "v", "subtitle", "text-author"}
WORD = re.compile(r"\w+", re.UNICODE)


class _Blocks(HTMLParser):
    """Collects the text of the innermost open block element, so a quote holding paragraphs counts its paragraphs."""

    def __init__(self, blocks):
        super().__init__(convert_charrefs=True)
        self.blocks = blocks
        self.stack = []

    def handle_starttag(self, tag, attrs):
        tag = tag.split("}")[-1]
        if tag in self.blocks:
            self.stack.append([])

    def handle_endtag(self, tag):
        tag = tag.split("}")[-1]
        if tag in self.blocks and self.stack:
            self.done.append(" ".join(self.stack.pop()))

    def handle_data(self, data):
        if self.stack:
            self.stack[-1].append(data)

    done = None


def _words_of_markup(text, blocks):
    parser = _Blocks(blocks)
    parser.done = []
    parser.feed(text)
    return [len(WORD.findall(block)) for block in parser.done]


def _epub(path):
    counts = []
    with zipfile.ZipFile(path) as book:
        for name in book.namelist():
            if name.lower().endswith((".xhtml", ".html", ".htm")):
                counts += _words_of_markup(book.read(name).decode("utf-8", "replace"), HTML_BLOCKS)
    return counts


def _fb2(path):
    return _words_of_markup(path.read_text("utf-8", "replace"), FB2_BLOCKS)


LIST_ITEM = re.compile(r"^\s*([-*+]|\d+\.)\s+")


def _markdown(path):
    counts = []
    for chunk in re.split(r"\n\s*\n", path.read_text("utf-8", "replace")):
        lines = [line for line in chunk.splitlines() if line.strip()]
        if not lines or lines[0].lstrip().startswith(("```", "---")):
            continue
        paragraph = []
        for line in lines:
            if LIST_ITEM.match(line):
                if paragraph:
                    counts.append(len(WORD.findall(" ".join(paragraph))))
                    paragraph = []
                counts.append(len(WORD.findall(LIST_ITEM.sub("", line))))
            else:
                paragraph.append(re.sub(r"^\s*#+\s+", "", line))
        if paragraph:
            counts.append(len(WORD.findall(" ".join(paragraph))))
    return counts


def _txt(path):
    return [len(WORD.findall(chunk)) for chunk in re.split(r"\n\s*\n", path.read_text("utf-8", "replace"))]


READERS = {".epub": _epub, ".fb2": _fb2, ".md": _markdown, ".markdown": _markdown, ".txt": _txt}


def block_words(path):
    """Words per non-empty block of the book at path, in reading order."""
    path = Path(path)
    reader = READERS.get(path.suffix.lower())
    if reader is None:
        raise ValueError(f"unsupported format: {path.suffix}")
    return [count for count in reader(path) if count > 0]


def summarise(counts):
    """Mean, median and per-band share of blocks and of words for a list of words-per-block counts."""
    total_blocks, total_words = len(counts), sum(counts)
    bands = {}
    for label, low, high in BANDS:
        inside = [c for c in counts if c >= low and (high is None or c <= high)]
        bands[label] = (len(inside) / total_blocks if total_blocks else 0.0,
                        sum(inside) / total_words if total_words else 0.0)
    return {"blocks": total_blocks, "words": total_words,
            "mean": statistics.fmean(counts) if counts else 0.0,
            "median": statistics.median(counts) if counts else 0.0, "bands": bands}


def render(name, result):
    lines = [f"{name}: {result['blocks']} blocks, {result['words']} words, "
             f"mean {result['mean']:.1f}, median {result['median']:.0f} words per block",
             "  band     blocks   words"]
    for label, (blocks, words) in result["bands"].items():
        lines.append(f"  {label:<8} {blocks:6.1%} {words:7.1%}")
    return "\n".join(lines)


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 2
    for book in argv:
        print(render(Path(book).name, summarise(block_words(book))))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
