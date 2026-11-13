"""Tests for scripts/md-to-books.py (small invented Markdown, a temp dir, no network)."""
import importlib.util
import socket
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from unittest import mock

SCRIPT = Path(__file__).resolve().parent.parent / "md-to-books.py"
spec = importlib.util.spec_from_file_location("md_to_books", SCRIPT)
mtb = importlib.util.module_from_spec(spec)
sys.modules["md_to_books"] = mtb
spec.loader.exec_module(mtb)

FB = "{http://www.gribuser.ru/xml/fictionbook/2.0}"
XL = "{http://www.w3.org/1999/xlink}"
PNG = b"\x89PNG\r\n\x1a\n" + b"tiny-image-bytes"

SAMPLE = """\
# Sample Book

> **Summary:** A short invented summary with AT&T.

## 3. Title

Text with AT&T, a < b > c, `x < y`, ~~gone~~, ***both***, a\\*b and a hard\x20\x20
break.<!-- hidden -->

- [x] done
- [ ] todo
  - nested
- plain

4. four
5. five

```mermaid
graph TD
    A[x] --> B[y]
```

| Left | Right |
|:--|--:|
| `a` | b & c |

> [!NOTE]
> Careful here.

![local](./pic.png)

![remote](https://example.com/a.jpg)

[ref link][r] and [anchor](#3-title) and <kbd>Ctrl</kbd> H<sub>2</sub>O.

[r]: https://example.com/ref

<details>
<summary>More</summary>

Hidden **text**.

</details>

$$
E = mc^2
$$

## Second

Final & done.
"""


class ConverterTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)
        self.src = self.tmp / "sample.md"
        self.src.write_text(SAMPLE, encoding="utf-8")
        (self.tmp / "pic.png").write_bytes(PNG)

    def convert(self, out="out", source=None):
        out_dir = self.tmp / out
        src = source or self.src
        mtb.convert(src, out_dir, "uk", "Sample", "Ann Author", ["epub", "fb2"])
        return out_dir

    def epub(self):
        return zipfile.ZipFile(self.convert() / "sample.epub")

    def test_epub_mimetype_first_and_stored(self):
        z = self.epub()
        first = z.infolist()[0]
        self.assertEqual("mimetype", first.filename)
        self.assertEqual(zipfile.ZIP_STORED, first.compress_type)
        self.assertEqual(b"application/epub+zip", z.read("mimetype"))

    def test_epub_layout_and_chapters_per_h2(self):
        names = self.epub().namelist()
        for expected in ("META-INF/container.xml", "OEBPS/content.opf", "OEBPS/nav.xhtml", "OEBPS/styles.css",
                         "OEBPS/images/pic.png", "OEBPS/text/chapter-001.xhtml", "OEBPS/text/chapter-002.xhtml",
                         "OEBPS/text/chapter-003.xhtml"):
            self.assertIn(expected, names)

    def test_every_epub_xml_part_is_well_formed(self):
        z = self.epub()
        for name in z.namelist():
            if name.endswith((".xhtml", ".opf", ".xml")):
                ET.fromstring(z.read(name))

    def test_opf_metadata_and_media_types(self):
        opf = self.epub().read("OEBPS/content.opf").decode()
        self.assertIn("<dc:language>uk</dc:language>", opf)
        self.assertIn("<dc:creator>Ann Author</dc:creator>", opf)
        self.assertIn('media-type="image/png"', opf)
        self.assertIn('properties="remote-resources"', opf)
        self.assertRegex(opf, r"urn:uuid:[0-9a-f-]{36}")

    def test_ampersand_is_escaped_in_both_formats(self):
        z = self.epub()
        chapter = z.read("OEBPS/text/chapter-001.xhtml").decode()
        self.assertIn("AT&amp;T", chapter)
        root = ET.fromstring(chapter.encode())
        self.assertIn("AT&T", "".join(root.itertext()))
        fb2 = (self.tmp / "out" / "sample.fb2").read_text(encoding="utf-8")
        self.assertIn("AT&amp;T", fb2)

    def test_less_than_is_escaped_in_text_and_code(self):
        chapter = self.epub().read("OEBPS/text/chapter-002.xhtml").decode()
        self.assertIn("a &lt; b &gt; c", chapter)
        self.assertIn("<code>x &lt; y</code>", chapter)

    def test_mermaid_fence_stays_verbatim_inside_pre(self):
        chapter = self.epub().read("OEBPS/text/chapter-002.xhtml").decode()
        self.assertIn('<pre><code class="language-mermaid">graph TD\n    A[x] --&gt; B[y]</code></pre>', chapter)

    def test_math_block_kept_verbatim_as_code(self):
        chapter = self.epub().read("OEBPS/text/chapter-002.xhtml").decode()
        self.assertIn('<code class="language-math">$$\nE = mc^2\n$$</code>', chapter)

    def test_numbered_heading_text_stays_as_typed(self):
        z = self.epub()
        self.assertIn(">3. Title</h2>", z.read("OEBPS/text/chapter-002.xhtml").decode())
        self.assertIn(">3. Title</a>", z.read("OEBPS/nav.xhtml").decode())

    def test_nav_links_and_internal_anchor_resolve(self):
        z = self.epub()
        nav = z.read("OEBPS/nav.xhtml").decode()
        self.assertIn('epub:type="toc"', nav)
        self.assertIn('href="text/chapter-002.xhtml#s-3-title"', nav)
        self.assertIn('href="chapter-002.xhtml#s-3-title"', z.read("OEBPS/text/chapter-002.xhtml").decode())

    def test_inline_and_block_constructs_in_epub(self):
        chapter = self.epub().read("OEBPS/text/chapter-002.xhtml").decode()
        for fragment in ("<del>gone</del>", "<em><strong>both</strong></em>", "a*b", "<br/>",
                         '<ol start="4">', '<span class="task-box">☑</span> done', "<li>nested</li>",
                         '<th class="align-right">Right</th>', '<td class="align-right">b &amp; c</td>',
                         'class="alert alert-note"', '<a href="https://example.com/ref">ref link</a>',
                         "<kbd>Ctrl</kbd>", "<sub>2</sub>", "<details>", "<summary>More</summary>",
                         '<img src="../images/pic.png" alt="local"/>',
                         '<img src="https://example.com/a.jpg" alt="remote"/>'):
            self.assertIn(fragment, chapter)
        self.assertNotIn("hidden", chapter)

    def test_fb2_is_well_formed_with_namespace_and_binary(self):
        out = self.convert()
        root = ET.fromstring((out / "sample.fb2").read_bytes())
        self.assertEqual(FB + "FictionBook", root.tag)
        binary = root.find(FB + "binary")
        self.assertEqual("pic.png", binary.get("id"))
        self.assertEqual("image/png", binary.get("content-type"))
        self.assertIn("image", [e.tag.replace(FB, "") for e in root.iter() if e.get(XL + "href") == "#pic.png"])

    def test_fb2_description_and_structure(self):
        root = ET.fromstring((self.convert() / "sample.fb2").read_bytes())
        info = root.find(f"{FB}description/{FB}title-info")
        self.assertEqual("Sample", info.find(FB + "book-title").text)
        self.assertEqual("uk", info.find(FB + "lang").text)
        self.assertEqual("science", info.find(FB + "genre").text)
        self.assertIn("A short invented summary", "".join(info.find(FB + "annotation").itertext()))
        titles = ["".join(t.itertext()) for t in root.iter(FB + "title")]
        self.assertIn("3. Title", titles)
        self.assertIsNotNone(root.find(f".//{FB}cite"))
        self.assertIsNotNone(root.find(f".//{FB}table"))
        self.assertIsNotNone(root.find(f".//{FB}strikethrough"))

    def test_fb2_sections_never_mix_blocks_and_subsections(self):
        src = self.tmp / "nest.md"
        src.write_text("# T\n\n## A\n\nloose\n\n### B\n\ninner\n", encoding="utf-8")
        root = ET.fromstring((self.convert(source=src) / "nest.fb2").read_bytes())
        for section in root.iter(FB + "section"):
            tags = {c.tag for c in section if c.tag != FB + "title"}
            self.assertFalse(FB + "section" in tags and len(tags) > 1)

    def test_fb2_remote_image_is_a_link_not_a_binary(self):
        root = ET.fromstring((self.convert() / "sample.fb2").read_bytes())
        links = [a.get(XL + "href") for a in root.iter(FB + "a")]
        self.assertIn("https://example.com/a.jpg", links)
        self.assertEqual(1, len(root.findall(FB + "binary")))

    def test_two_runs_give_byte_identical_files(self):
        a, b = self.convert("one"), self.convert("two")
        for name in ("sample.epub", "sample.fb2"):
            self.assertEqual((a / name).read_bytes(), (b / name).read_bytes())

    def test_different_source_changes_book_id(self):
        first = self.epub().read("OEBPS/content.opf")
        self.src.write_text(SAMPLE + "\nextra\n", encoding="utf-8")
        second = zipfile.ZipFile(self.convert("again") / "sample.epub").read("OEBPS/content.opf")
        self.assertNotEqual(first, second)

    def test_missing_local_image_degrades_to_alt_text(self):
        src = self.tmp / "miss.md"
        src.write_text("# T\n\n![nothing here](./gone.png)\n", encoding="utf-8")
        z = zipfile.ZipFile(self.convert(source=src) / "miss.epub")
        self.assertIn('<span class="missing-image">nothing here</span>',
                      z.read("OEBPS/text/chapter-001.xhtml").decode())

    def test_no_network_is_attempted(self):
        with mock.patch.object(socket.socket, "connect", side_effect=AssertionError("network used")):
            self.convert()

    def test_formats_flag_limits_output(self):
        out = self.tmp / "only"
        mtb.convert(self.src, out, "en", None, "A", ["fb2"])
        self.assertEqual(["sample.fb2"], [p.name for p in out.iterdir()])

    def test_reference_links_task_free_emphasis_edge_cases(self):
        nodes = mtb.InlineParser({}).parse("snake_case_name and 2 * 3 * 4 and **bold *it***")
        text = mtb.plain(nodes)
        self.assertEqual("snake_case_name and 2 * 3 * 4 and bold it", text)


if __name__ == "__main__":
    unittest.main()
