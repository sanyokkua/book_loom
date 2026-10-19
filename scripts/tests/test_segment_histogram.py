import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "segment-histogram.py"
SPEC = importlib.util.spec_from_file_location("segment_histogram", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
HIST = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HIST)

# Blocks of 2, 6 and 12 words; the heading has 2 words, the first paragraph 6 and the quote's paragraph 12.
XHTML = ("<html><body><h1>Chapter One</h1><p>one two three four five six</p>"
         "<blockquote><p>a b c d e f g h i j k l</p></blockquote></body></html>")


class BlockWordsTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def write(self, name, text):
        path = Path(self.dir.name) / name
        path.write_text(text, "utf-8")
        return path

    def test_epub_countsHeadingParagraphAndQuotedParagraph(self):
        path = Path(self.dir.name) / "b.epub"
        with zipfile.ZipFile(path, "w") as book:
            book.writestr("OEBPS/c1.xhtml", XHTML)
        self.assertEqual(HIST.block_words(path), [2, 6, 12])

    def test_fb2_countsParagraphsAndVerses(self):
        path = self.write("b.fb2", "<FictionBook><body><section><p>one two</p><poem><stanza><v>a b c</v></stanza>"
                                   "</poem></section></body></FictionBook>")
        self.assertEqual(HIST.block_words(path), [2, 3])

    def test_markdown_dropsMarkersAndCountsBlocks(self):
        path = self.write("b.md", "# Title here\n\nOne two three.\n\n- item one\n- item two\n")
        self.assertEqual(HIST.block_words(path), [2, 3, 2, 2])

    def test_txt_splitsOnBlankLines(self):
        path = self.write("b.txt", "one two\nthree\n\nfour\n\n\nfive six\n")
        self.assertEqual(HIST.block_words(path), [3, 1, 2])


class SummariseTest(unittest.TestCase):
    def test_bandsShareOfBlocksAndOfWords(self):
        result = HIST.summarise([2, 6, 12, 40])
        self.assertEqual(result["blocks"], 4)
        self.assertEqual(result["words"], 60)
        self.assertEqual(result["median"], 9)
        self.assertEqual(result["bands"]["1-3"], (0.25, 2 / 60))
        self.assertEqual(result["bands"][">30"], (0.25, 40 / 60))


if __name__ == "__main__":
    unittest.main()
