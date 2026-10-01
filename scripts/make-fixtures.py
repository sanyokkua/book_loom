#!/usr/bin/env python3
"""Generate the "Earth Gravity" fixture book in TXT, Markdown, FB2 and EPUB from one source.

The book below (``PARTS``) is the single source of truth: every format, the images, the dummy font and
``manifest.json`` are rendered from it, deterministically (fixed zip timestamps, sorted JSON), so running
the script twice gives byte-identical files and ``validate-translated-book.py --self-test`` can prove the
committed fixtures have not drifted from it.

Usage:
    python3 scripts/make-fixtures.py            # (re)write modules/app/src/test/resources/fixtures/earth-gravity
    python3 scripts/make-fixtures.py --out DIR  # write somewhere else (the self-test uses a temp dir)

Standard library only. Inline markup in the source is a tiny XML dialect parsed with ElementTree:
    <i>…</i> italic · <b>…</b> bold · <a href="…">…</a> link · <dc>X</dc> drop cap on the first letter
    <fn n="1"/> footnote reference · <code>…</code> code/formula span · <f lang="fr">…</f> foreign run
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import struct
import sys
import textwrap
import xml.etree.ElementTree as ET
import zipfile
import zlib
from pathlib import Path
from xml.sax.saxutils import escape as _xml_escape

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "modules/app/src/test/resources/fixtures/earth-gravity"
BASENAME = "earth-gravity"
FORMATS = ("txt", "md", "fb2", "epub")

NBSP = "\u00a0"
ZWSP = "\u200b"
INVISIBLE = "\u00a0\u200b\u200c\u200d\u2060\ufeff"
ZIP_TIME = (2026, 1, 1, 0, 0, 0)
BOOK_UUID = "urn:uuid:5b0e7c1a-3d2f-4e8a-9c41-0f6e2a7d9b13"

TITLE = "Earth Gravity"
AUTHOR = "Dr. Eleanor Vance"
DESCRIPTION = (
    "A short practical introduction to Earth’s gravity, told through the notes of Dr. Eleanor Vance "
    "and her team at Harrow Vale."
)
REPEATED = "Every object with mass attracts every other object with mass."
HEADING_TWIN = "Gravity keeps the oceans in place"
COVER_ALT = "Cover"

# The chunk budget the pipeline packs against (modules/pipeline/.../chunk/TokenBudget.MAX_CHUNK_TOKENS) and the
# estimator's figures (TokenEstimator: code points / chars-per-token × 1.15; Latin 4.0, unknown 3.0). The long
# paragraph must exceed the budget on its own so the oversized-segment split runs.
CHUNK_BUDGET_TOKENS = 1200
SAFETY_FACTOR = 1.15
CHARS_PER_TOKEN = {"en": 4.0, "unknown": 3.0}

LONG_PARAGRAPH = (
    "At nine o’clock in the evening we carried the pendulum up the hill, four of us on the poles and Reyes walking "
    "backwards in front, calling out every stone on the path as if it were a personal enemy. The observatory at "
    "Harrow Vale is older than the Institute itself; its dome was built for a telescope that was sold a century ago, "
    "and what remains is a round stone room with a hook in the centre of the ceiling and a floor that was levelled, "
    "the plaque says, by a man who did not believe in rushing. We hung the bob from the hook at ten, and for the "
    "first hour nothing happened that would interest anyone but us. The bob swung, the counter clicked, the "
    "thermometer said eleven degrees, and the barometer said that the weather would hold. I wrote down the period "
    "every five minutes, and Reyes read it back to me so that neither of us could blame the other later. At eleven "
    "the wind rose, and the dome began to hum a low note that we could feel in our teeth. The pendulum did not care. "
    "That is the first thing a person learns about gravity at night: it does not care about the wind, the cold, the "
    "hour, or the number of cups of tea the observers have drunk. It pulls the bob toward the centre of the Earth "
    "with the same quiet insistence it applied to the stones of the hill when the glaciers left them there. At "
    "midnight we changed the length of the wire by exactly one centimetre and began again, because a single "
    "measurement is an opinion and two measurements are the beginning of an argument. The new period was longer, "
    "as it should have been, and the difference matched the formula to the fourth decimal place. Reyes wanted to "
    "celebrate; I made him write the number down twice first. Around one o’clock a fox came to the door and watched "
    "us for a long time with the polite suspicion of a neighbour who has heard noises. We must have looked strange "
    "to it: two people in coats, sitting on folding stools beside a brass weight that swung and swung and never "
    "arrived anywhere. I thought about the Amulet, locked in its crate in the station below, and about the "
    "villagers who said that it pulled harder than any honest weight should. It does not, of course. We weighed it "
    "on three separate balances before we left the Institute, and it weighs exactly what a piece of brass of its "
    "size ought to weigh, no more and no less. But I understood the villagers better that night than I had before. "
    "When you watch a pendulum for long enough, the steadiness of it begins to feel like intention, as if something "
    "under the floor were patiently drawing the bob back each time it tried to escape. At two o’clock the counter "
    "jammed, and we lost eleven minutes of data while Reyes took it apart on his knees with a torch in his mouth. He "
    "fixed it with a hairpin borrowed from me and a word I will not write in an Institute notebook. At three the "
    "clouds cleared and the stars came out over the open slot of the dome, so many of them that the room seemed to "
    "tilt. I remember thinking that every one of those stars was pulling on our little brass bob too, each from its "
    "own enormous distance, and that the inverse-square law had made all of their pulls together smaller than the "
    "pull of the hill we were sitting on. That is the second thing a person learns about gravity at night: distance "
    "is a kind of mercy. If the stars pulled as hard as the Earth, nothing would ever fall in a straight line, and "
    "no one could ever hang a pendulum and trust it. At four o’clock we were both too tired to talk, so we took "
    "turns reading the numbers aloud, and the numbers became a kind of song, the same four digits rising and falling "
    "with the swing. Reyes said afterwards that he dreamed of them for a week. Somewhere after half past four he "
    "asked me, very seriously, whether the pendulum would still swing if nobody were watching it, and I told him "
    "that the Institute had been founded precisely so that somebody would always be watching it. He did not laugh, "
    "which I took as a sign of how tired he was. We talked instead about the first people who had done this kind of "
    "work, the surveyors who carried brass pendulums across mountains on the backs of mules and lay awake in tents "
    "counting swings by candlelight, and about how little the method has changed since then: a weight, a wire, a "
    "clock, patience, and the honesty to write down the number that the instrument gives you rather than the number "
    "you hoped for. Reyes said that patience was the only part of the kit the Institute had never managed to "
    "requisition. At five the sky in the east turned "
    "the colour of weak tea, and the first birds began to argue in the heather. We had nine hours of readings, two "
    "broken pencils, one repaired counter and a result that differed from the textbook value by a little less than "
    "two parts in ten thousand, which is exactly what the slate under the valley predicted. Nell, I told myself, "
    "this is the night you will describe to every student who asks why anyone measures gravity by hand. I wrote the "
    "final figure on the inside cover of the notebook in ink, so that nobody could rub it out, and underneath it I "
    "wrote the date, the place and both our names."
)

NAMES = [
    {"name": "Dr. Eleanor Vance", "kind": "person", "forms": ["Eleanor", "Vance", "Nell"],
     "note": "forename + surname + nickname; the narrator of chapter six"},
    {"name": "Tomas Reyes", "kind": "person", "forms": ["Tomas", "Reyes"], "note": "forename + surname"},
    {"name": "Harrow Vale", "kind": "place", "forms": ["Harrow"], "note": "two-word place; 'Vale' is also a common noun"},
    {"name": "The Meridian Survey Institute", "kind": "organisation", "forms": ["Meridian"],
     "note": "also referred to as 'the Institute'"},
    {"name": "the Amulet", "kind": "object", "forms": ["Amulet"], "note": "an object with a capital"},
]
GLOSSARY_BAIT = ["Well", "Don’t", "Words", "Seal", "Shield"]
TERMS = ["km", "m/s²", "9.81", "6.674×10⁻¹¹", "N·m²/kg²", "mGal", "784.8 N"]


# --------------------------------------------------------------------------------------------------------------
# The book. Block constructors produce plain dicts; ids are assigned per part in assign_ids().
# --------------------------------------------------------------------------------------------------------------

def H(x: str) -> dict:
    return {"t": "h", "x": x}


def P(x: str, lang: str | None = None, wrap: bool = False, cls: str | None = None) -> dict:
    return {"t": "p", "x": x, "lang": lang, "wrap": wrap, "cls": cls}


def PRE(text: str) -> dict:
    return {"t": "pre", "text": text}


def UL(*items: str) -> dict:
    return {"t": "list", "ordered": False, "items": list(items)}


def OL(*items: str) -> dict:
    return {"t": "list", "ordered": True, "items": list(items)}


def TABLE(header: list[str], *rows: list[str]) -> dict:
    return {"t": "table", "rows": [header, *rows]}


def QUOTE(*paras: str, epigraph: bool = False) -> dict:
    return {"t": "quote", "paras": list(paras), "epigraph": epigraph}


def POEM(title: str, *stanzas: list[str]) -> dict:
    return {"t": "poem", "title": title, "stanzas": [list(s) for s in stanzas]}


def FIG(image: str, alt: str, caption: str) -> dict:
    return {"t": "fig", "image": image, "alt": alt, "caption": caption}


def IMGP(image: str, alt: str) -> dict:
    return {"t": "imgp", "image": image, "alt": alt}


CHAPTER_TITLES = [
    "Chapter One: What Is Gravity?",
    "Chapter Two: How Earth’s Gravity Works",
    "Chapter Three: The Gravity Formula",
    "Chapter Four: The Harrow Vale Expedition",
    "Chapter Five: Gravity and Distance",
    "Chapter Six: The Long Night at the Observatory",
    "Chapter Seven: Songs of Falling Things",
]

FRONT = [
    {"id": "tp", "file": "title", "kind": "title", "title": None, "blocks": [
        P("A Practical Introduction", cls="subtitle"),
        P("by Dr. Eleanor Vance", cls="byline"),
        P("The Meridian Survey Institute", cls="imprint"),
    ]},
    {"id": "cr", "file": "copyright", "kind": "front", "title": "Copyright", "blocks": [
        P("Copyright © 2026 The Meridian Survey Institute. All rights reserved."),
        P("This edition was prepared as a test book for BookLoom. Any resemblance to a real survey, a real valley "
          "or a real amulet is a coincidence."),
        P("978-0-00-000000-2"),
        P("First edition, Harrow Vale, 2026."),
    ]},
    {"id": "toc", "file": "contents", "kind": "toc", "title": "Contents", "blocks": []},
]

CHAPTERS = [
    [
        QUOTE("<b>Summary:</b> Gravity is the force that attracts objects with mass toward one another. On Earth, it "
              "keeps people, oceans, and the atmosphere close to the planet and causes unsupported objects to fall "
              "toward the ground.", epigraph=True),
        P("<dc>G</dc>ravity is one of the fundamental interactions in nature. Dr. Eleanor Vance liked to begin every "
          "lecture with that sentence, and then with a pause long enough for somebody at the back to drop a pen.",
          cls="first"),
        P(REPEATED),
        P("For everyday objects this attraction is extremely small. However, Earth has a mass of approximately "
          "<code>5.972 × 10²⁴ kg</code>, and because Earth is so massive, its gravitational attraction is strong "
          "enough to keep us on its surface."),
        P("“Well, that is the whole trick,” Vance said. “Mass pulls on mass — always, everywhere, without "
          "exception.”"),
        P("“Don’t say <i>everywhere</i>,” Tomas Reyes objected. “Say <i>everywhere we have measured</i>.”"),
        P("She laughed. Her friends had called her Nell since school, and only Reyes still dared to argue with "
          "Dr. Vance in front of a lecture hall."),
        H("Mass and weight"),
        UL("<b>Mass</b> describes how much matter an object contains.",
           "<b>Weight</b> describes the gravitational force acting on that mass."),
        P("The average gravitational acceleration near Earth’s surface is approximately <b>9.81 metres per second "
          "every second</b>, written <code>g = 9.81 m/s²</code>.<fn n=\"1\"/>"),
        PRE("g = 9.81 m/s²"),
        FIG("earth.jpg", "Earth from space", "Earth seen from space, about 29,000 km away."),
        P("Your mass would remain the same on Earth and on the Moon, but your weight would be much lower on the "
          "Moon — roughly one sixth of what the bathroom scale shows at home."),
        P("Words matter here. Words like <i>heavy</i> and <i>light</i> describe weight, not mass, and Vance never "
          "let a student mix them up."),
    ],
    [
        P("Earth attracts objects toward its <b>centre of mass</b>. Nell drew it on the board as a single dot with "
          "arrows pointing inward from every direction."),
        FIG("diagram.png", "diagram", "Figure 1. Arrows pointing toward the centre of mass."),
        P("For example:"),
        OL("You release an object.",
           "Earth’s gravity accelerates the object toward Earth’s centre.",
           "The ground prevents the object from continuing farther.",
           "The object therefore remains on Earth’s surface."),
        P("Some important consequences of gravity include:"),
        UL("Objects fall toward Earth.",
           "The Moon remains in orbit around Earth.",
           "Earth remains in orbit around the Sun.",
           "Oceans remain on Earth’s surface.",
           "Earth’s atmosphere remains gravitationally bound to the planet."),
        H(HEADING_TWIN),
        P(HEADING_TWIN),
        P("Shield your eyes when you look at the tide charts, Reyes liked to joke, because the numbers are "
          "blinding: the Moon lifts the open ocean by less than a metre, and yet it moves whole bays."),
        P("Seal the instrument case before the descent, the checklist said, and Nell always read it aloud."),
    ],
    [
        P("Newton’s law of universal gravitation is usually written <code>F = G × (m₁ × m₂) / r²</code>, and every "
          "symbol in it has a plain meaning."),
        PRE("F = G × (m₁ × m₂) / r²"),
        TABLE(["Symbol", "Meaning", "Typical unit"],
              ["<code>F</code>", "Gravitational force", "newton (<code>N</code>)"],
              ["<code>G</code>", "Gravitational constant", "<code>N·m²/kg²</code>"],
              ["<code>m₁</code>", "Mass of the first object", "kilogram (<code>kg</code>)"],
              ["<code>m₂</code>", "Mass of the second object", "kilogram (<code>kg</code>)"],
              ["<code>r</code>", "Distance between the centres of mass", "metre (<code>m</code>)"]),
        P("The gravitational constant is approximately 6.674×10⁻¹¹ N·m²/kg². It is so small that two people "
          "standing a metre apart attract each other with less force than a grain of dust exerts on a table."),
        P("2"),
        P("For an 80 kg person on Earth’s surface, the force comes out at about 784.8 N — the familiar weight of "
          "that person."),
        P("Vance kept the worked example pinned above her desk as a numbered plate:"),
        P("XIV"),
        P("§"),
        P("Every result in this section assumes that Earth is a perfect sphere; the next chapter shows what happens "
          "when it is not."),
        P("***", cls="break"),
        P("9.81"),
    ],
    [
        P("The <b>Meridian Survey Institute</b> sent its first gravity team to Harrow Vale in the spring, and "
          "Dr. Eleanor Vance led it."),
        P("Harrow Vale is a long valley of slate and heather about 40 km from the coast. Its floor lies 212 m above "
          "sea level, and its gravity is a fraction weaker than the textbook value."),
        P("“Is the Amulet really in the crate?” Reyes asked on the first morning."),
        P("“It is,” said Nell, “and it stays there — wrapped, sealed and logged — until we have measured the valley "
          "without it.”"),
        P("The Amulet was not magic, whatever the villagers said. It was a dense brass weight, polished by a century "
          "of hands, that the Institute used to calibrate its gravimeters."),
        P("Above the door of the old field station someone had carved a line in Latin:"),
        P("Gravitas omnia trahit, sed nemo videt.", lang="la"),
        P("Reyes translated it at once — “weight pulls everything, but nobody sees it” — and Vance said that the "
          "motto was more accurate than most textbooks."),
        P("In the station log, a French visitor had added a note of her own: <f lang=\"fr\">La pesanteur ne prend "
          "jamais de vacances.</f> Nobody ever crossed it out."),
        P("On the next page she had written <f lang=\"fr\">« Ce n’est qu’un poids »</f> — <i>it is only a "
          "weight</i> — beside a sketch of the Amulet."),
        H("Field notes"),
        P("Day one: wind from the west, light rain, readings steady. Day two: the gravimeter drifted by 0.02 mGal an "
          "hour and Reyes recalibrated it twice against the Amulet. Day three: the readings at the north end of "
          "Harrow Vale were lower than the readings at the south end, exactly as the slate maps predicted.",
          wrap=True),
        P("Don’t trust a single reading, Vance wrote in the margin. Trust the pattern."),
    ],
    [
        P("Gravity becomes weaker as distance increases. According to the <b><i>inverse-square law</i></b>, "
          "doubling the distance from Earth’s centre leaves only a quarter of the pull."),
        PRE("Gravity ∝ 1 / distance²"),
        TABLE(["Distance from Earth’s centre", "Relative strength"],
              ["1 × radius", "100%"], ["2 × radius", "25%"], ["3 × radius", "11.1%"], ["4 × radius", "6.25%"]),
        P(REPEATED),
        IMGP("diagram.png", "diagram"),
        P("The same diagram, drawn again at a larger scale, shows the arrows thinning out as they spread over a "
          "bigger sphere."),
        FIG("diagram.png", "diagram", "Figure 2. The same arrows, spread over a sphere twice as large."),
        QUOTE("“What goes up must come down” is a useful everyday approximation, although orbital mechanics makes "
              "the real situation far more interesting."),
        P("Read more at <a href=\"https://science.nasa.gov/solar-system/\">NASA Solar System Exploration</a> or in "
          "the <a href=\"https://en.wikipedia.org/wiki/Newton%27s_law_of_universal_gravitation\">article on "
          "Newton’s law</a>."),
        P("Satellites stay in orbit because they are always falling toward Earth and always missing it.<fn n=\"2\"/>"),
    ],
    [
        P("Nell kept a diary during the night the Institute’s pendulum was moved to the observatory. One entry ran "
          "on for pages."),
        P(LONG_PARAGRAPH, cls="long"),
        P("When the sun rose, the pendulum was still swinging."),
    ],
    [
        P("Reyes, who wrote verse when the instruments were quiet, left a short poem in the log of Harrow Vale."),
        POEM("The Plumb Line",
             ["A stone let go will find the ground,", "it never asks the way;",
              "the Earth is calling all around", "and every stone obeys."],
             ["The Moon is falling, so they say,", "and missing us for good —",
              "it has been falling night and day", "the way a planet should."]),
        P(NBSP),
        P("Seal."),
        P("Shield."),
        P("Words."),
        P(ZWSP),
        P("Well…"),
        P(REPEATED),
        P("“Is that all?” asked Tomas. “That is all,” Dr. Vance replied, ‘and it is enough.’"),
        P("Gravity is identical everywhere on Earth — or so the old textbook claimed.<fn n=\"3\"/> The Harrow Vale "
          "survey proved it wrong by a few parts in ten thousand."),
        P("The Amulet went back into its case, the case went back to the Institute, and Nell went back to her "
          "lectures."),
    ],
]

NOTES = [
    "The standard value adopted in 1901 is exactly 9.80665 m/s²; 9.81 is the rounded figure most textbooks use.",
    "An orbit is a fall that never ends: the ground curves away as fast as the satellite drops.",
    "The difference between the equator and the poles is about 0.5%, mostly because Earth spins and bulges at the "
    "equator.",
]

IMAGE_MEDIA = {"cover.png": "image/png", "diagram.png": "image/png", "earth.jpg": "image/jpeg"}
FONT_FILE = "BookLoomTestFont-DUMMY.otf"


def build_parts() -> list[dict]:
    parts = [dict(p, blocks=list(p["blocks"])) for p in FRONT]
    toc = parts[2]
    toc["blocks"] = [{"t": "toc", "items": [(f"ch{i + 1}", t) for i, t in enumerate(CHAPTER_TITLES)]}]
    for i, (title, blocks) in enumerate(zip(CHAPTER_TITLES, CHAPTERS)):
        parts.append({"id": f"ch{i + 1}", "file": f"ch{i + 1}", "kind": "chapter", "title": title, "blocks": blocks})
    parts.append({"id": "notes", "file": "notes", "kind": "notes", "title": "Notes",
                  "blocks": [{"t": "note", "n": n + 1, "x": text} for n, text in enumerate(NOTES)]})
    for part in parts:
        assign_ids(part)
    return parts


def assign_ids(part: dict) -> None:
    pid = part["id"]
    counters: dict[str, int] = {}

    def nxt(key: str) -> int:
        counters[key] = counters.get(key, 0) + 1
        return counters[key]

    part["title_id"] = f"{pid}-title" if part["title"] else None
    for block in part["blocks"]:
        t = block["t"]
        if t in ("p", "h", "pre", "imgp"):
            block["id"] = f"{pid}-{t}{nxt(t)}"
        elif t == "list":
            k = nxt("l")
            block["id"] = f"{pid}-l{k}"
            block["ids"] = [f"{pid}-l{k}-{i + 1}" for i in range(len(block["items"]))]
        elif t == "toc":
            block["id"] = f"{pid}-l1"
            block["ids"] = [f"{pid}-l1-{i + 1}" for i in range(len(block["items"]))]
        elif t == "table":
            k = nxt("t")
            block["id"] = f"{pid}-t{k}"
            block["ids"] = [[f"{pid}-t{k}-r{r}c{c}" for c in range(len(row))] for r, row in enumerate(block["rows"])]
        elif t == "quote":
            k = nxt("q")
            block["id"] = f"{pid}-q{k}"
            block["ids"] = [f"{pid}-q{k}-{i + 1}" for i in range(len(block["paras"]))]
        elif t == "poem":
            k = nxt("poem")
            block["id"] = f"{pid}-poem{k}"
            block["title_id"] = f"{pid}-poem{k}-t"
            block["ids"] = [[f"{pid}-poem{k}-s{s + 1}v{v + 1}" for v in range(len(st))]
                            for s, st in enumerate(block["stanzas"])]
        elif t == "fig":
            k = nxt("fig")
            block["id"] = f"{pid}-fig{k}"
            block["cap_id"] = f"{pid}-fig{k}-cap"
        elif t == "note":
            block["id"] = f"note-{block['n']}"
        else:
            raise ValueError(t)


# --------------------------------------------------------------------------------------------------------------
# Inline markup: parse, render per format, plain text per format, logical counts.
# --------------------------------------------------------------------------------------------------------------

def parse_inline(x: str) -> ET.Element:
    return ET.fromstring(f"<r>{x}</r>")


def identity(text: str) -> str:
    return text


def inline_logical_counts(x: str) -> dict:
    counts: dict[str, int] = {}
    for el in parse_inline(x).iter():
        if el.tag != "r":
            counts[el.tag] = counts.get(el.tag, 0) + 1
    return dict(sorted(counts.items()))


def xesc(text: str) -> str:
    return _xml_escape(text, {'"': "&quot;"})


def render_inline(x: str, fmt: str, tx=identity, part_file: str = "") -> str:
    root = parse_inline(x)
    return _render_node_children(root, fmt, tx, part_file)


def _render_node_children(node: ET.Element, fmt: str, tx, part_file: str) -> str:
    out = [_text(node.text, fmt, tx)]
    for child in node:
        out.append(_render_el(child, fmt, tx, part_file))
        out.append(_text(child.tail, fmt, tx))
    return "".join(out)


def _text(text: str | None, fmt: str, tx) -> str:
    if not text:
        return ""
    text = tx(text)
    if fmt in ("epub", "fb2"):
        return xesc(text)
    if fmt == "md":
        return md_escape(text)
    return text


def _render_el(el: ET.Element, fmt: str, tx, part_file: str) -> str:
    inner = _render_node_children(el, fmt, tx, part_file)
    raw = el.text or ""
    tag = el.tag
    if fmt == "txt":
        return {"fn": f"[{el.get('n')}]", "code": raw, "f": raw}.get(tag, inner)
    if fmt == "md":
        return _md_el(el, tag, inner, raw)
    if fmt == "fb2":
        return _fb2_el(el, tag, inner, raw)
    return _epub_el(el, tag, inner, raw)


def _md_el(el: ET.Element, tag: str, inner: str, raw: str) -> str:
    if tag == "i":
        return f"*{inner}*"
    if tag == "b":
        return f"**{inner}**"
    if tag == "a":
        return f"[{inner}]({el.get('href')})"
    if tag == "fn":
        return f"[{el.get('n')}](#note-{el.get('n')})"
    if tag == "code":
        return f"`{raw}`"
    if tag == "f":
        return md_escape(raw)
    return inner  # dc: the letter stays plain text in Markdown


def _fb2_el(el: ET.Element, tag: str, inner: str, raw: str) -> str:
    if tag == "i":
        return f"<emphasis>{inner}</emphasis>"
    if tag == "b":
        return f"<strong>{inner}</strong>"
    if tag == "a":
        return f'<a l:href="{xesc(el.get("href"))}">{inner}</a>'
    if tag == "fn":
        return f'<a l:href="#n{el.get("n")}" type="note">{el.get("n")}</a>'
    if tag == "code":
        return f"<code>{xesc(raw)}</code>"
    if tag == "f":
        return f'<style name="foreign" xml:lang="{el.get("lang")}">{xesc(raw)}</style>'
    return f'<style name="dropcap">{inner}</style>'


def _epub_el(el: ET.Element, tag: str, inner: str, raw: str) -> str:
    if tag == "i":
        return f"<em>{inner}</em>"
    if tag == "b":
        return f"<strong>{inner}</strong>"
    if tag == "a":
        return f'<a href="{xesc(el.get("href"))}">{inner}</a>'
    if tag == "fn":
        n = el.get("n")
        return f'<a epub:type="noteref" id="fnref{n}" href="notes.xhtml#fn{n}"><sup>{n}</sup></a>'
    if tag == "code":
        return f"<code>{xesc(raw)}</code>"
    if tag == "f":
        lang = el.get("lang")
        return f'<span lang="{lang}" xml:lang="{lang}">{xesc(raw)}</span>'
    return f'<span class="dropcap">{inner}</span>'


def plain_inline(x: str, fmt: str) -> str:
    """The visible text a reader of `fmt` sees in the block (what the validator extracts), whitespace-collapsed."""
    root = parse_inline(x)

    def walk(node: ET.Element) -> str:
        out = [node.text or ""]
        for child in node:
            if child.tag == "fn":
                out.append(f"[{child.get('n')}]" if fmt == "txt" else child.get("n"))
            else:
                out.append(walk(child))
            out.append(child.tail or "")
        return "".join(out)

    return norm(walk(root))


def norm(text: str) -> str:
    return re.sub(r"[ \t\r\n\f]+", " ", text).strip(" \t\r\n\f")


MD_SPECIAL = set("\\`*_[]<>#|")


def md_escape(text: str) -> str:
    return "".join("\\" + ch if ch in MD_SPECIAL else ch for ch in text)


def category(x: str, lang: str | None = None) -> str:
    """translatable | verbatim | foreign | invisible | code, mirroring the pipeline's VerbatimRule."""
    if lang:
        return "foreign"
    root = parse_inline(x)
    outside_code = _text_outside(root, {"code"})
    visible = "".join(ch for ch in norm(outside_code) if ch not in INVISIBLE).strip()
    if not visible:
        return "code" if root.find(".//code") is not None else "invisible"
    letters = "".join(ch for ch in visible if ch.isalpha())
    if not letters:
        return "verbatim"
    if re.fullmatch(r"M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})", letters) and not any(
            ch.isdigit() for ch in visible):
        return "verbatim"
    return "verbatim" if len(visible) == 1 else "translatable"


def _text_outside(node: ET.Element, skip: set[str]) -> str:
    out = [node.text or ""]
    for child in node:
        if child.tag not in skip:
            out.append(_text_outside(child, skip))
        out.append(child.tail or "")
    return "".join(out)


# --------------------------------------------------------------------------------------------------------------
# Per-format block inventory: the list the validator expects, in reading order.
# --------------------------------------------------------------------------------------------------------------

def entry(bid: str, kind: str, x: str, fmt: str, lang: str | None = None, **extra) -> dict:
    record = {"id": bid, "kind": kind, "category": category(x, lang), "text": plain_inline(x, fmt),
              "inline": inline_logical_counts(x)}
    record.update(extra)
    return record


def blocks_for(fmt: str, parts: list[dict]) -> list[dict]:
    out: list[dict] = []
    for part in parts:
        if part["kind"] == "title":
            out.append(entry("tp-h1", "heading", TITLE, fmt))
        elif part["title"]:
            out.append(entry(part["title_id"], "heading", part["title"], fmt))
        for block in part["blocks"]:
            out.extend(_block_entries(block, fmt))
    return out


def _block_entries(b: dict, fmt: str) -> list[dict]:
    t = b["t"]
    if t == "p":
        return [entry(b["id"], "paragraph", b["x"], fmt, b["lang"])]
    if t == "h":
        return [entry(b["id"], "heading", b["x"], fmt)]
    if t == "pre":
        return [] if fmt == "txt" else [dict(entry(b["id"], "pre", f"<code>{xesc(b['text'])}</code>", fmt),
                                             inline={})]
    if t == "imgp":
        return [] if fmt == "txt" else [{"id": b["id"], "kind": "image-paragraph", "category": "image", "text": "",
                                         "inline": {"img": 1}}]
    if t == "fig":
        return _fig_entries(b, fmt)
    if t in ("list", "toc"):
        return _list_entries(b, fmt)
    if t == "table":
        return _table_entries(b, fmt)
    if t == "quote":
        return [entry(i, "quote", x, fmt) for i, x in zip(b["ids"], b["paras"])]
    if t == "poem":
        return _poem_entries(b, fmt)
    if t == "note":
        title = [{"id": f"{b['id']}-title", "kind": "note-title", "category": "verbatim", "text": str(b["n"]),
                  "inline": {}}] if fmt == "fb2" else []
        return title + [entry(b["id"], "note", note_markup(b, fmt), fmt)]
    raise ValueError(t)


def note_origin(n: int) -> str:
    """The file stem of the part whose text carries footnote reference n — the target of the note's backlink."""
    marker = f'<fn n="{n}"/>'
    for part in build_parts_cache():
        for b in part["blocks"]:
            if marker in json.dumps(b, ensure_ascii=False).replace('\\"', '"'):
                return part["file"]
    raise ValueError(n)


def note_markup(b: dict, fmt: str) -> str:
    """A note's inline source as each format writes it: a backlink in EPUB and Markdown, a [n] label in TXT."""
    n = b["n"]
    if fmt == "epub":
        return b["x"] + f' <a href="{note_origin(n)}.xhtml#fnref{n}">↩</a>'
    if fmt == "md":
        return b["x"] + f' <a href="#ref-{n}">↩</a>'
    if fmt == "txt":
        return f"[{n}] " + b["x"]
    return b["x"]


def _fig_entries(b: dict, fmt: str) -> list[dict]:
    """A figure's caption is a block in every format; only Markdown writes the image as a paragraph of its own
    (EPUB and FB2 hold it in a figure/section-level image, which the validator counts as an image, not a block)."""
    cap = entry(b["cap_id"], "caption", b["caption"], fmt)
    if fmt != "md":
        return [cap]
    return [{"id": b["id"], "kind": "image-paragraph", "category": "image", "text": "", "inline": {"img": 1}}, cap]


def _list_items(b: dict) -> list[str]:
    if b["t"] == "toc":
        return [f'<a href="#{target}">{xesc(title)}</a>' for target, title in b["items"]]
    return b["items"]


def _list_prefix(b: dict, i: int) -> str:
    if b["t"] == "toc":
        return ""
    return f"{i + 1}. " if b["ordered"] else "• "


def _list_entries(b: dict, fmt: str) -> list[dict]:
    items = _list_items(b)
    if fmt == "txt":
        lines = [_list_prefix(b, i) + plain_inline(x, fmt) for i, x in enumerate(items)]
        rec = {"id": b["id"], "kind": "list", "category": "translatable", "text": norm(" ".join(lines)),
               "inline": {}, "lines": len(lines)}
        return [rec]
    if fmt == "fb2":
        return [entry(i, "list-item", xesc(_list_prefix(b, n)) + x, fmt) for n, (i, x) in enumerate(zip(b["ids"], items))]
    return [entry(i, "list-item", x, fmt) for i, x in zip(b["ids"], items)]


def _table_entries(b: dict, fmt: str) -> list[dict]:
    if fmt == "txt":
        lines = [" | ".join(plain_inline(c, fmt) for c in row) for row in b["rows"]]
        return [{"id": b["id"], "kind": "table", "category": "translatable", "text": norm(" ".join(lines)),
                 "inline": {}, "lines": len(lines)}]
    out = []
    for r, row in enumerate(b["rows"]):
        for c, x in enumerate(row):
            out.append(entry(b["ids"][r][c], "table-header" if r == 0 else "table-cell", x, fmt))
    return out


def _poem_entries(b: dict, fmt: str) -> list[dict]:
    out = [entry(b["title_id"], "poem-title", b["title"], fmt)]
    for s, stanza in enumerate(b["stanzas"]):
        if fmt in ("txt", "md"):
            out.append({"id": f"{b['id']}-s{s + 1}", "kind": "stanza", "category": "translatable",
                        "text": norm(" ".join(stanza)), "inline": {}, "lines": len(stanza)})
        else:
            out.extend(entry(b["ids"][s][v], "verse", line, fmt) for v, line in enumerate(stanza))
    return out


# --------------------------------------------------------------------------------------------------------------
# TXT
# --------------------------------------------------------------------------------------------------------------

def render_txt(parts: list[dict], tx=identity) -> str:
    paras: list[str] = []
    for part in parts:
        if part["kind"] == "title":
            paras.append(tx(TITLE))
        elif part["title"]:
            paras.append(tx(part["title"]))
        for b in part["blocks"]:
            paras.extend(_txt_block(b, tx))
    return "\n\n".join(paras) + "\n"


def _txt_block(b: dict, tx) -> list[str]:
    t = b["t"]
    if t in ("p", "h"):
        text = render_inline(b["x"], "txt", tx if _translatable(b) else identity)
        return [textwrap.fill(text, width=72) if b.get("wrap") else text]
    if t in ("pre", "imgp"):
        return []
    if t == "fig":
        return [render_inline(b["caption"], "txt", tx)]
    if t in ("list", "toc"):
        items = _list_items(b)
        return ["\n".join(_list_prefix(b, i) + render_inline(x, "txt", tx) for i, x in enumerate(items))]
    if t == "table":
        return ["\n".join(" | ".join(_cell_txt(c, tx) for c in row) for row in b["rows"])]
    if t == "quote":
        return ["    " + render_inline(x, "txt", tx) for x in b["paras"]]
    if t == "poem":
        return [tx(b["title"])] + ["\n".join(tx(v) for v in st) for st in b["stanzas"]]
    if t == "note":
        return [render_inline(note_markup(b, "txt"), "txt", tx)]
    raise ValueError(t)


def _cell_txt(x: str, tx) -> str:
    return render_inline(x, "txt", tx if category(x) == "translatable" else identity)


def _translatable(b: dict) -> bool:
    return category(b["x"], b.get("lang")) == "translatable"


# --------------------------------------------------------------------------------------------------------------
# Markdown
# --------------------------------------------------------------------------------------------------------------

def slug(title: str) -> str:
    return re.sub(r"[^a-z0-9 -]", "", title.lower().replace("’", "")).replace(" ", "-")


def render_md(parts: list[dict], tx=identity, lang: str = "en") -> str:
    head = ["---", f'title: "{tx(TITLE)}"', f'author: "{tx(AUTHOR)}"', f"lang: {lang}",
            f'description: "{tx(DESCRIPTION)}"', "---", ""]
    blocks: list[str] = []
    titles = {f"ch{i + 1}": slug(t) for i, t in enumerate(CHAPTER_TITLES)}
    for part in parts:
        if part["kind"] == "title":
            blocks.append("# " + md_escape(tx(TITLE)))
        elif part["title"]:
            blocks.append("## " + md_escape(tx(part["title"])))
        for b in part["blocks"]:
            blocks.extend(_md_block(b, tx, titles))
    return "\n".join(head) + "\n" + "\n\n".join(blocks) + "\n"


def _md_block(b: dict, tx, titles: dict) -> list[str]:
    t = b["t"]
    if t == "p":
        text = render_inline(b["x"], "md", tx if _translatable(b) else identity)
        return [text]
    if t == "h":
        return ["### " + render_inline(b["x"], "md", tx)]
    if t == "pre":
        return ["```text\n" + b["text"] + "\n```"]
    if t == "imgp":
        return [f"![{md_escape(b['alt'])}](images/{b['image']})"]
    if t == "fig":
        return [f"![{md_escape(b['alt'])}](images/{b['image']})", "*" + render_inline(b["caption"], "md", tx) + "*"]
    if t == "toc":
        return ["\n".join(f"- [{md_escape(tx(title))}](#{titles[target]})" for target, title in b["items"])]
    if t == "list":
        return ["\n".join(("1. " if b["ordered"] else "- ") + render_inline(x, "md", tx) for x in b["items"])]
    if t == "table":
        return [_md_table(b, tx)]
    if t == "quote":
        return ["\n>\n".join("> " + render_inline(x, "md", tx) for x in b["paras"])]
    if t == "poem":
        return ["**" + md_escape(tx(b["title"])) + "**"] + [
            "\\\n".join(md_escape(tx(v)) for v in st) for st in b["stanzas"]]
    if t == "note":
        return [f"{b['n']}. " + render_inline(note_markup(b, "md"), "md", tx)]
    raise ValueError(t)


def _md_table(b: dict, tx) -> str:
    rows = [[_md_cell(c, tx) for c in row] for row in b["rows"]]
    lines = ["| " + " | ".join(rows[0]) + " |", "|" + "---|" * len(rows[0])]
    lines += ["| " + " | ".join(r) + " |" for r in rows[1:]]
    return "\n".join(lines)


def _md_cell(x: str, tx) -> str:
    return render_inline(x, "md", tx if category(x) == "translatable" else identity)


# --------------------------------------------------------------------------------------------------------------
# FB2
# --------------------------------------------------------------------------------------------------------------

FB2_CSS = ".dropcap { font-size: 200%; float: left; } .foreign { font-style: italic; }"


def render_fb2(parts: list[dict], images: dict[str, bytes], tx=identity, lang: str = "en") -> str:
    body: list[str] = []
    front = parts[0]
    body.append("  <title>")
    body.append(f'   <p id="tp-h1">{xesc(tx(TITLE))}</p>')
    for b in front["blocks"]:
        body.append(f'   <p id="{b["id"]}">{render_inline(b["x"], "fb2", tx)}</p>')
    body.append("  </title>")
    for part in parts[1:]:
        if part["kind"] == "notes":
            continue
        body.append(f'  <section id="{part["id"]}">')
        body.append(f'   <title><p id="{part["title_id"]}">{xesc(tx(part["title"]))}</p></title>')
        for b in part["blocks"]:
            body.extend("   " + line for line in _fb2_block(b, tx))
        body.append("  </section>")
    notes = parts[-1]
    note_lines = [f'  <title><p id="{notes["title_id"]}">{xesc(tx(notes["title"]))}</p></title>']
    for b in notes["blocks"]:
        note_lines.append(f'  <section id="n{b["n"]}"><title><p id="{b["id"]}-title">{b["n"]}</p></title>'
                          f'<p id="{b["id"]}">{render_inline(b["x"], "fb2", tx)}</p></section>')
    binaries = [f'<binary id="{name}" content-type="{IMAGE_MEDIA[name]}">{base64.b64encode(data).decode()}</binary>'
                for name, data in images.items()]
    return "\n".join([
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">',
        f'<stylesheet type="text/css">{FB2_CSS}</stylesheet>',
        _fb2_description(tx, lang),
        "<body>", *body, "</body>",
        '<body name="notes">', *note_lines, "</body>",
        *binaries,
        "</FictionBook>",
    ]) + "\n"


def _fb2_description(tx, lang: str) -> str:
    return "\n".join([
        "<description>",
        " <title-info>",
        "  <genre>sci_phys</genre>",
        "  <author><first-name>" + xesc(tx("Eleanor")) + "</first-name><last-name>" + xesc(tx("Vance"))
        + "</last-name><nickname>" + xesc(tx("Nell")) + "</nickname></author>",
        f"  <book-title>{xesc(tx(TITLE))}</book-title>",
        f'  <annotation><p id="ann-1">{xesc(tx(DESCRIPTION))}</p></annotation>',
        "  <keywords>gravity, physics, test fixture</keywords>",
        '  <date value="2026-01-01">2026</date>',
        '  <coverpage><image l:href="#cover.png"/></coverpage>',
        f"  <lang>{lang}</lang>",
        "  <src-lang>en</src-lang>",
        " </title-info>",
        " <document-info>",
        "  <author><nickname>BookLoom</nickname></author>",
        "  <program-used>scripts/make-fixtures.py</program-used>",
        '  <date value="2026-01-01">2026</date>',
        "  <id>5b0e7c1a-3d2f-4e8a-9c41-0f6e2a7d9b13</id>",
        "  <version>1.0</version>",
        " </document-info>",
        " <publish-info><publisher>The Meridian Survey Institute</publisher><year>2026</year>"
        "<isbn>978-0-00-000000-2</isbn></publish-info>",
        "</description>",
    ])


def _fb2_p(bid: str, x: str, tx, lang: str | None = None) -> str:
    attrs = f' id="{bid}"' + (f' xml:lang="{lang}"' if lang else "")
    use = tx if category(x, lang) == "translatable" else identity
    return f"<p{attrs}>{render_inline(x, 'fb2', use)}</p>"


def _fb2_block(b: dict, tx) -> list[str]:
    t = b["t"]
    if t == "p":
        lines = [_fb2_p(b["id"], b["x"], tx, b["lang"])]
        return ["<empty-line/>", *lines, "<empty-line/>"] if b.get("cls") == "break" else lines
    if t == "h":
        return [f'<subtitle id="{b["id"]}">{render_inline(b["x"], "fb2", tx)}</subtitle>']
    if t == "pre":
        return [f'<p id="{b["id"]}"><code>{xesc(b["text"])}</code></p>']
    if t == "imgp":
        return [f'<p id="{b["id"]}"><image l:href="#{b["image"]}" alt="{xesc(b["alt"])}"/></p>']
    if t == "fig":
        return [f'<image l:href="#{b["image"]}" alt="{xesc(b["alt"])}" id="{b["id"]}"/>',
                _fb2_p(b["cap_id"], b["caption"], tx)]
    if t in ("list", "toc"):
        return [_fb2_p(i, xesc(_list_prefix(b, n)) + x, tx) for n, (i, x) in enumerate(zip(b["ids"], _list_items(b)))]
    if t == "table":
        return _fb2_table(b, tx)
    if t == "quote":
        tag = "epigraph" if b["epigraph"] else "cite"
        return [f"<{tag}>", *(_fb2_p(i, x, tx) for i, x in zip(b["ids"], b["paras"])), f"</{tag}>"]
    if t == "poem":
        out = ["<poem>", f'<title><p id="{b["title_id"]}">{xesc(tx(b["title"]))}</p></title>']
        for s, stanza in enumerate(b["stanzas"]):
            out.append("<stanza>" + "".join(f'<v id="{b["ids"][s][v]}">{xesc(tx(line))}</v>'
                                            for v, line in enumerate(stanza)) + "</stanza>")
        return out + ["</poem>"]
    raise ValueError(t)


def _fb2_table(b: dict, tx) -> list[str]:
    out = ["<table>"]
    for r, row in enumerate(b["rows"]):
        cell = "th" if r == 0 else "td"
        cells = "".join(
            f'<{cell} id="{b["ids"][r][c]}">'
            f'{render_inline(x, "fb2", tx if category(x) == "translatable" else identity)}</{cell}>'
            for c, x in enumerate(row))
        out.append(f"<tr>{cells}</tr>")
    return out + ["</table>"]


# --------------------------------------------------------------------------------------------------------------
# EPUB
# --------------------------------------------------------------------------------------------------------------

EPUB_CSS = f"""@font-face {{
  font-family: "BookLoom Test Dummy";
  src: url("../fonts/{FONT_FILE}") format("opentype");
}}
body {{ font-family: "BookLoom Test Dummy", serif; margin: 0 5%; }}
.dropcap {{ float: left; font-size: 3em; line-height: 0.8; margin-right: 0.08em; }}
.subtitle, .byline, .imprint {{ text-align: center; }}
.break {{ text-align: center; }}
.center {{ text-align: center; }}
.verse {{ margin-left: 2em; }}
blockquote.epigraph {{ font-style: italic; }}
code {{ font-family: monospace; }}
"""


def xhtml(title: str, body: str, body_type: str, tx=identity, lang: str = "en") -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE html>\n'
        f'<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" '
        f'xml:lang="{lang}" lang="{lang}">\n'
        f'<head>\n<meta charset="UTF-8"/>\n<title>{xesc(tx(title))}</title>\n'
        '<link rel="stylesheet" type="text/css" href="../styles/book.css"/>\n</head>\n'
        f'<body epub:type="{body_type}">\n{body}\n</body>\n</html>\n'
    )


def _epub_p(bid: str, x: str, tx, lang: str | None = None, cls: str | None = None) -> str:
    attrs = f' id="{bid}"'
    if cls:
        attrs += f' class="{cls}"'
    if lang:
        attrs += f' lang="{lang}" xml:lang="{lang}"'
    use = tx if category(x, lang) == "translatable" else identity
    return f"<p{attrs}>{render_inline(x, 'epub', use)}</p>"


def _epub_block(b: dict, tx) -> list[str]:
    t = b["t"]
    if t == "p":
        return [_epub_p(b["id"], b["x"], tx, b["lang"], b.get("cls"))]
    if t == "h":
        return [f'<h3 id="{b["id"]}">{render_inline(b["x"], "epub", tx)}</h3>']
    if t == "pre":
        return [f'<pre id="{b["id"]}"><code>{xesc(b["text"])}</code></pre>']
    if t == "imgp":
        return [f'<p id="{b["id"]}" class="center"><img src="../images/{b["image"]}" alt="{xesc(b["alt"])}"/></p>']
    if t == "fig":
        return [f'<figure id="{b["id"]}-box"><img id="{b["id"]}" src="../images/{b["image"]}" '
                f'alt="{xesc(b["alt"])}"/>',
                f'<figcaption id="{b["cap_id"]}">{render_inline(b["caption"], "epub", tx)}</figcaption></figure>']
    if t in ("list", "toc"):
        tag = "ol" if b["t"] == "toc" or b["ordered"] else "ul"
        items = [x.replace('href="#', 'href="') for x in _list_items(b)]
        items = [re.sub(r'href="(ch\d+)"', r'href="\1.xhtml"', x) for x in items]
        return [f"<{tag}>", *(f'<li id="{i}">{render_inline(x, "epub", tx)}</li>' for i, x in zip(b["ids"], items)),
                f"</{tag}>"]
    if t == "table":
        return _epub_table(b, tx)
    if t == "quote":
        cls = ' class="epigraph"' if b["epigraph"] else ""
        return [f"<blockquote{cls}>", *(_epub_p(i, x, tx) for i, x in zip(b["ids"], b["paras"])), "</blockquote>"]
    if t == "poem":
        out = [f'<div class="poem" id="{b["id"]}">', f'<h4 id="{b["title_id"]}">{xesc(tx(b["title"]))}</h4>']
        for s, stanza in enumerate(b["stanzas"]):
            out.append('<div class="stanza">' + "".join(
                f'<p class="verse" id="{b["ids"][s][v]}">{xesc(tx(line))}</p>' for v, line in enumerate(stanza))
                       + "</div>")
        return out + ["</div>"]
    if t == "note":
        n = b["n"]
        return [f'<aside epub:type="footnote" id="fn{n}"><p id="{b["id"]}">'
                f'{render_inline(note_markup(b, "epub"), "epub", tx)}</p></aside>']
    raise ValueError(t)


def _epub_table(b: dict, tx) -> list[str]:
    out = ["<table>"]
    for r, row in enumerate(b["rows"]):
        cell = "th" if r == 0 else "td"
        cells = "".join(
            f'<{cell} id="{b["ids"][r][c]}">'
            f'{render_inline(x, "epub", tx if category(x) == "translatable" else identity)}</{cell}>'
            for c, x in enumerate(row))
        out.append(f"<tr>{cells}</tr>")
    return out + ["</table>"]


_PARTS_CACHE: list[dict] = []


def build_parts_cache() -> list[dict]:
    if not _PARTS_CACHE:
        _PARTS_CACHE.extend(build_parts())
    return _PARTS_CACHE


def epub_documents(parts: list[dict], tx=identity, lang: str = "en") -> list[tuple[str, str, str]]:
    """(file stem, epub:type, xhtml) for every spine document, cover first."""
    docs = [("cover", "cover", xhtml(TITLE, f'<div class="cover" id="cover"><img src="../images/cover.png" '
                                            f'alt="{COVER_ALT}"/></div>', "cover", tx, lang))]
    for part in parts:
        lines: list[str] = []
        if part["kind"] == "title":
            lines.append(f'<h1 id="tp-h1">{xesc(tx(TITLE))}</h1>')
        elif part["title"]:
            lines.append(f'<h2 id="{part["title_id"]}">{xesc(tx(part["title"]))}</h2>')
        for b in part["blocks"]:
            lines.extend(_epub_block(b, tx))
        body_type = {"title": "frontmatter titlepage", "front": "frontmatter copyright-page", "toc": "frontmatter toc",
                     "chapter": "bodymatter chapter", "notes": "backmatter footnotes"}[part["kind"]]
        body = f'<section id="{part["id"]}">\n' + "\n".join(lines) + "\n</section>"
        docs.append((part["file"], body_type, xhtml(TITLE, body, body_type, tx, lang)))
    return docs


def nav_labels(parts: list[dict]) -> list[tuple[str, str]]:
    return [(p["file"], p["title"] or TITLE) for p in parts]


def render_nav(parts: list[dict], tx=identity, lang: str = "en") -> str:
    toc = "\n".join(f'<li><a href="text/{f}.xhtml">{xesc(tx(label))}</a></li>' for f, label in nav_labels(parts))
    body = (f'<nav epub:type="toc" id="toc"><h1>{xesc(tx("Contents"))}</h1>\n<ol>\n{toc}\n</ol>\n</nav>\n'
            '<nav epub:type="landmarks" id="landmarks" hidden="hidden"><h1>Landmarks</h1>\n<ol>\n'
            '<li><a epub:type="cover" href="text/cover.xhtml">Cover</a></li>\n'
            '<li><a epub:type="toc" href="text/contents.xhtml">Contents</a></li>\n'
            '<li><a epub:type="bodymatter" href="text/ch1.xhtml">Start</a></li>\n</ol>\n</nav>')
    return xhtml(TITLE, body, "frontmatter", tx, lang).replace("../styles/book.css", "styles/book.css")


def render_ncx(parts: list[dict], tx=identity) -> str:
    points = "\n".join(
        f'  <navPoint id="np{i + 1}" playOrder="{i + 1}"><navLabel><text>{xesc(tx(label))}</text></navLabel>'
        f'<content src="text/{f}.xhtml"/></navPoint>' for i, (f, label) in enumerate(nav_labels(parts)))
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">\n'
            f'<head><meta name="dtb:uid" content="{BOOK_UUID}"/><meta name="dtb:depth" content="1"/>'
            '<meta name="dtb:totalPageCount" content="0"/><meta name="dtb:maxPageNumber" content="0"/></head>\n'
            f'<docTitle><text>{xesc(tx(TITLE))}</text></docTitle>\n'
            f'<docAuthor><text>{xesc(tx(AUTHOR))}</text></docAuthor>\n'
            f'<navMap>\n{points}\n</navMap>\n</ncx>\n')


def render_opf(docs: list[tuple[str, str, str]], tx=identity, lang: str = "en") -> str:
    manifest = ['<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>',
                '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>',
                '<item id="css" href="styles/book.css" media-type="text/css"/>',
                f'<item id="font" href="fonts/{FONT_FILE}" media-type="font/otf"/>',
                '<item id="cover-image" href="images/cover.png" media-type="image/png" properties="cover-image"/>',
                '<item id="img-diagram" href="images/diagram.png" media-type="image/png"/>',
                '<item id="img-earth" href="images/earth.jpg" media-type="image/jpeg"/>']
    manifest += [f'<item id="x-{stem}" href="text/{stem}.xhtml" media-type="application/xhtml+xml"/>'
                 for stem, _, _ in docs]
    spine = [f'<itemref idref="x-{stem}"/>' for stem, _, _ in docs]
    return "\n".join([
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">',
        ' <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">',
        f'  <dc:identifier id="bookid">{BOOK_UUID}</dc:identifier>',
        f"  <dc:title>{xesc(tx(TITLE))}</dc:title>",
        f"  <dc:creator>{xesc(tx(AUTHOR))}</dc:creator>",
        f"  <dc:language>{lang}</dc:language>",
        f"  <dc:description>{xesc(tx(DESCRIPTION))}</dc:description>",
        "  <dc:publisher>The Meridian Survey Institute</dc:publisher>",
        '  <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>',
        '  <meta name="cover" content="cover-image"/>',
        " </metadata>",
        " <manifest>", *("  " + m for m in manifest), " </manifest>",
        ' <spine toc="ncx">', *("  " + s for s in spine), " </spine>",
        ' <guide><reference type="cover" title="Cover" href="text/cover.xhtml"/></guide>',
        "</package>",
    ]) + "\n"


CONTAINER_XML = ('<?xml version="1.0" encoding="UTF-8"?>\n'
                 '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
                 '<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
                 '</rootfiles>\n</container>\n')


def render_epub(parts: list[dict], images: dict[str, bytes], font: bytes, tx=identity, lang: str = "en") -> bytes:
    docs = epub_documents(parts, tx, lang)
    entries: list[tuple[str, bytes]] = [
        ("META-INF/container.xml", CONTAINER_XML.encode()),
        ("OEBPS/content.opf", render_opf(docs, tx, lang).encode()),
        ("OEBPS/toc.ncx", render_ncx(parts, tx).encode()),
        ("OEBPS/nav.xhtml", render_nav(parts, tx, lang).encode()),
        ("OEBPS/styles/book.css", EPUB_CSS.encode()),
        (f"OEBPS/fonts/{FONT_FILE}", font),
    ]
    entries += [(f"OEBPS/images/{name}", data) for name, data in images.items()]
    entries += [(f"OEBPS/text/{stem}.xhtml", doc.encode()) for stem, _, doc in docs]
    return write_zip([("mimetype", b"application/epub+zip")] + entries)


def write_zip(entries: list[tuple[str, bytes]]) -> bytes:
    import io
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for i, (name, data) in enumerate(entries):
            info = zipfile.ZipInfo(name, date_time=ZIP_TIME)
            info.compress_type = zipfile.ZIP_STORED if i == 0 else zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data)
    return buffer.getvalue()


# --------------------------------------------------------------------------------------------------------------
# Images and the dummy font, generated with the standard library only.
# --------------------------------------------------------------------------------------------------------------

def png(width: int, height: int, pixel) -> bytes:
    rows = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(width)) for y in range(height))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")


def cover_pixel(x: int, y: int) -> tuple[int, int, int]:
    dx, dy = x - 60, y - 100
    if dx * dx + dy * dy < 38 * 38:
        return (40 + (x * 2) % 60, 110 + (y % 40), 200) if (x + y) % 23 > 6 else (60, 160, 80)
    return (12, 18, 48 + y // 6)


def diagram_pixel(x: int, y: int) -> tuple[int, int, int]:
    cx = cy = 32
    on_axis = abs(x - cx) <= 1 or abs(y - cy) <= 1 or abs((x - cx) - (y - cy)) <= 1 or abs((x - cx) + (y - cy)) <= 1
    if abs(x - cx) <= 3 and abs(y - cy) <= 3:
        return (200, 30, 30)
    return (30, 30, 30) if on_axis and 6 < max(abs(x - cx), abs(y - cy)) < 28 else (250, 250, 245)


def jpeg_gray(width: int, height: int, level) -> bytes:
    """A baseline grayscale JPEG whose 8×8 blocks carry only a DC value (one flat tone per block)."""
    quant = 8
    dc_bits = [0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0]
    dc_vals = list(range(12))
    ac_bits = [0, 2] + [0] * 14
    ac_vals = [0x00, 0x01]
    dc_codes = _canonical_codes(dc_bits, dc_vals)
    ac_eob = _canonical_codes(ac_bits, ac_vals)[0x00]
    bits: list[str] = []
    previous = 0
    for by in range(0, height, 8):
        for bx in range(0, width, 8):
            value = round((level(bx + 4, by + 4) - 128) * 8 / quant)
            diff, previous = value - previous, value
            size = abs(diff).bit_length()
            bits.append(dc_codes[size])
            if size:
                bits.append(format(diff if diff > 0 else diff + (1 << size) - 1, f"0{size}b"))
            bits.append(ac_eob)
    stream = "".join(bits)
    stream += "1" * (-len(stream) % 8)
    data = bytearray()
    for i in range(0, len(stream), 8):
        byte = int(stream[i:i + 8], 2)
        data.append(byte)
        if byte == 0xFF:
            data.append(0x00)

    def segment(marker: int, payload: bytes) -> bytes:
        return struct.pack(">HH", marker, len(payload) + 2) + payload

    return (b"\xff\xd8"
            + segment(0xFFE0, b"JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00")
            + segment(0xFFDB, b"\x00" + bytes([quant] * 64))
            + segment(0xFFC0, struct.pack(">BHHB", 8, height, width, 1) + b"\x01\x11\x00")
            + segment(0xFFC4, b"\x00" + bytes(dc_bits) + bytes(dc_vals))
            + segment(0xFFC4, b"\x10" + bytes(ac_bits) + bytes(ac_vals))
            + segment(0xFFDA, b"\x01\x01\x00\x00\x3f\x00")
            + bytes(data) + b"\xff\xd9")


def _canonical_codes(bits: list[int], vals: list[int]) -> dict[int, str]:
    codes, code, k = {}, 0, 0
    for length, count in enumerate(bits, start=1):
        for _ in range(count):
            codes[vals[k]] = format(code, f"0{length}b")
            code += 1
            k += 1
        code <<= 1
    return codes


def earth_level(x: int, y: int) -> int:
    dx, dy = x - 32, y - 24
    return 40 if dx * dx + dy * dy > 20 * 20 else 120 + (x * 3 + y * 5) % 90


def make_images() -> dict[str, bytes]:
    return {"cover.png": png(120, 180, cover_pixel), "diagram.png": png(64, 64, diagram_pixel),
            "earth.jpg": jpeg_gray(64, 48, earth_level)}


def make_font() -> bytes:
    """A 12-byte OpenType ('OTTO') table directory declaring zero tables: NOT a usable font, only a binary
    resource whose bytes must survive the round trip. Named DUMMY so nobody mistakes it for a real typeface."""
    return b"OTTO" + struct.pack(">HHHH", 0, 0, 0, 0)


# --------------------------------------------------------------------------------------------------------------
# Manifest
# --------------------------------------------------------------------------------------------------------------

def counts_by_category(blocks: list[dict]) -> dict[str, int]:
    out: dict[str, int] = {}
    for b in blocks:
        out[b["category"]] = out.get(b["category"], 0) + 1
    return dict(sorted(out.items()))


def image_count(fmt: str, parts: list[dict]) -> int:
    """Image references in the body, plus the cover page (EPUB) or coverpage (FB2); TXT carries none."""
    if fmt == "txt":
        return 0
    body = sum(1 for p in parts for b in p["blocks"] if b["t"] in ("fig", "imgp"))
    return body + (0 if fmt == "md" else 1)


def estimate_tokens(text: str, key: str) -> int:
    import math
    return math.ceil(len(text) / CHARS_PER_TOKEN[key] * SAFETY_FACTOR)


def aux_for(fmt: str, parts: list[dict]) -> dict:
    alts = []
    for part in parts:
        for b in part["blocks"]:
            if b["t"] in ("fig", "imgp"):
                alts.append(b["alt"])
    if fmt == "txt":
        return {}
    if fmt == "md":
        return {"frontmatter": {"title": TITLE, "author": AUTHOR, "description": DESCRIPTION}, "image_alts": alts}
    if fmt == "fb2":
        return {"book_title": TITLE, "author": ["Eleanor", "Vance", "Nell"], "annotation": [DESCRIPTION],
                "image_alts": alts}
    labels = [label for _, label in nav_labels(parts)]
    docs = epub_documents(parts)
    return {"dc_title": TITLE, "dc_creator": AUTHOR, "dc_description": DESCRIPTION,
            "head_titles": [TITLE] * (len(docs) + 1), "image_alts": [COVER_ALT] + alts,
            "nav_labels": labels, "ncx_labels": labels}


def build_manifest(parts: list[dict], images: dict[str, bytes], font: bytes) -> dict:
    formats = {}
    for fmt in FORMATS:
        blocks = blocks_for(fmt, parts)
        formats[fmt] = {
            "file": f"{BASENAME}.{fmt}",
            "block_count": len(blocks),
            "blocks_by_category": counts_by_category(blocks),
            "translatable_blocks": sum(1 for b in blocks if b["category"] == "translatable"),
            "images": image_count(fmt, parts),
            "footnote_refs": sum(b["inline"].get("fn", 0) for b in blocks),
            "footnotes": len(NOTES),
            "aux": aux_for(fmt, parts),
            "blocks": blocks,
        }
    long_id = next(b["id"] for p in parts for b in p["blocks"] if b.get("cls") == "long")
    repeated_ids = [b["id"] for p in parts for b in p["blocks"] if b["t"] == "p" and b["x"] == REPEATED]
    epub_blocks = formats["epub"]["blocks"]
    return {
        "book": {"title": TITLE, "author": AUTHOR, "language": "en", "generator": "scripts/make-fixtures.py",
                 "validator": "scripts/validate-translated-book.py"},
        "names": NAMES,
        "glossary_bait": GLOSSARY_BAIT,
        "terms": TERMS,
        "repeated_sentence": {"text": REPEATED, "ids": repeated_ids},
        "heading_equals_body": {"text": HEADING_TWIN,
                                "ids": [b["id"] for b in epub_blocks if b["text"] == HEADING_TWIN]},
        "long_paragraph": {
            "id": long_id, "words": len(LONG_PARAGRAPH.split()), "chars": len(LONG_PARAGRAPH),
            "estimated_tokens_en": estimate_tokens(LONG_PARAGRAPH, "en"),
            "estimated_tokens_unknown_language": estimate_tokens(LONG_PARAGRAPH, "unknown"),
            "chunk_budget_tokens": CHUNK_BUDGET_TOKENS},
        "verbatim_texts": sorted({b["text"] for b in epub_blocks if b["category"] == "verbatim"}),
        "foreign": {"blocks": [{"id": b["id"], "lang": "la", "text": b["text"]}
                               for b in epub_blocks if b["category"] == "foreign"],
                    "inline": [{"lang": "fr", "text": "La pesanteur ne prend jamais de vacances."},
                               {"lang": "fr", "text": "« Ce n’est qu’un poids »"}]},
        "invisible_blocks": [b["id"] for b in epub_blocks if b["category"] == "invisible"],
        "footnotes": NOTES,
        "links": ["https://science.nasa.gov/solar-system/",
                  "https://en.wikipedia.org/wiki/Newton%27s_law_of_universal_gravitation"],
        "binary_files": {
            **{f"images/{name}": hashlib.sha256(data).hexdigest() for name, data in images.items()},
            f"fonts/{FONT_FILE}": hashlib.sha256(font).hexdigest()},
        "formats": formats,
    }


# --------------------------------------------------------------------------------------------------------------
# Entry points
# --------------------------------------------------------------------------------------------------------------

def render_all(tx=identity, lang: str = "en") -> dict[str, bytes]:
    """Every fixture file, keyed by its path relative to the fixture directory."""
    parts = build_parts_cache()
    images = make_images()
    font = make_font()
    files = {
        f"{BASENAME}.txt": render_txt(parts, tx).encode(),
        f"{BASENAME}.md": render_md(parts, tx, lang).encode(),
        f"{BASENAME}.fb2": render_fb2(parts, images, tx, lang).encode(),
        f"{BASENAME}.epub": render_epub(parts, images, font, tx, lang),
    }
    files.update({f"images/{name}": data for name, data in images.items()})
    if tx is identity:
        manifest = build_manifest(parts, images, font)
        files["manifest.json"] = (json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode()
    return files


def write_all(out: Path, files: dict[str, bytes]) -> None:
    for rel, data in files.items():
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    files = render_all()
    write_all(args.out, files)
    for rel, data in sorted(files.items()):
        print(f"{len(data):>8}  {args.out / rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
