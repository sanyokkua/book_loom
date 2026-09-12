package ua.bookloom.document.fixture;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentKind;

/**
 * Every round-trip fixture, enumerated as a named case carrying its builder and its <strong>declared</strong>
 * expectations.
 *
 * <p><strong>Why declared and never computed.</strong> A catalogue that asked the walker how many segments it
 * produced would agree with the walker no matter what the walker did — which is exactly how a 73.74%-coverage
 * walker looked correct for a whole change. Every number below is written out by hand from reading the fixture,
 * and when one disagrees with the implementation the question is which of the two is wrong, not which to update.
 *
 * <p><strong>{@code coverageFloor} is the one declared number that is deliberately derived from a measurement,
 * not from hand-counting the fixture.</strong> It is not a statement about behaviour the way
 * {@code expectedSegmentCount} and {@code expectedKinds} are — it is a gate threshold, and a threshold has to be
 * calibrated against whatever it is a threshold on. Change {@code fix-document-round-trip-corpus-defects} task
 * 2.4 fixed two measurement defects in {@code TextCoverage} (Markdown's numerator carried raw markup against a
 * stripped denominator; TXT's denominator was decoded as UTF-8 regardless of the resolved charset) and then
 * <strong>re-derived every floor against the corrected metric</strong> — each is set to just under what the
 * corrected metric now reports for that fixture (within {@code 0.02}, per this capability's "declared floor is
 * not left slack" scenario), never restored to a round, hand-picked number. Do not "fix" an apparent
 * inconsistency between a floor and a hand-counted expectation elsewhere in this file by loosening a floor back
 * toward the old metric's numbers — that is exactly the slack this change closed. The one deliberate exception is
 * {@code md/only-a-fence}, whose floor is {@code 0.0} because the fixture has no translatable text at all: with
 * an empty denominator the coverage measurement reports {@code 1.0} by definition (a document with nothing to
 * translate is fully covered by producing nothing), so its floor is not a measurement of segmentation quality and
 * is carved out of the {@code 0.02}-slack check for that reason, not hand-counted either way.
 *
 * <p><strong>Why an enumeration at all.</strong> A fixture that exists but is not asserted proves nothing, and
 * that is not hypothetical: the shape that corrupts a translation containing inline markup had a place in no
 * fixture and no test, which is why the defect reached a release with the gate green. Registering a fixture here
 * is what gates it — the sweep picks it up with no further test being written.
 *
 * <p><strong>{@code expectedPlaceholderCount} is counted against the masking rules, not against what the walker
 * finds.</strong> A segment's <em>block</em> is the element the descent reaches once it owns direct text of its own
 * (ADR-0027); a purely-structural wrapper above it — {@code <span>}, {@code <i>} with no sibling text — sits
 * outside the segment and contributes no token. A masker that silently stopped emitting tokens would still pass
 * every identity check trivially — mask nothing, restore nothing, compare equal — which is what this column catches.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FixtureCatalog {

    /** How a format's no-edit round trip is compared; one per format, per design.md D7. */
    public enum Comparison {

        /** Decompressed canonical content, entry order, and mimetype-first/STORED. */
        EPUB_CANONICAL,

        /** Canonical XML over the re-parsed tree, plus the declared encoding value and each binary payload. */
        FB2_CANONICAL,

        /** A deterministic structural serialization of the re-parsed CommonMark tree. */
        MARKDOWN_AST,

        /** Exact bytes — the one format where that is the right assertion rather than an over-strict one. */
        TXT_BYTES
    }

    /**
     * One catalogued fixture.
     *
     * @param name the case's name, used as the parameterized test's display name
     * @param format the format the port must resolve this fixture to
     * @param fileName the name to write the fixture under; the extension is what format resolution reads
     * @param builder writes the fixture to the given path and returns it
     * @param expectedSegmentCount how many segments this fixture must yield, counted by hand from its source
     * @param expectedPlaceholderCount the total number of placeholder tokens across every one of this fixture's
     *     segments, counted by hand against the masking rules described at the class level — never copied from a
     *     measurement, for the same reason {@code expectedSegmentCount} is not
     * @param expectedKinds every kind this fixture must produce, and no other
     * @param coverageFloor the proportion of the fixture's visible text its segments must cover, with the
     *     excluded-block budget stated in each case below; derived from the corrected {@code TextCoverage}
     *     measurement per the class-level note above, not hand-counted like the other fields
     */
    public record Case(
            String name,
            BookFormat format,
            String fileName,
            UnaryOperator<Path> builder,
            int expectedSegmentCount,
            int expectedPlaceholderCount,
            Set<SegmentKind> expectedKinds,
            double coverageFloor,
            Comparison comparison) {

        public Case {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(builder, "builder");
            Objects.requireNonNull(expectedKinds, "expectedKinds");
            Objects.requireNonNull(comparison, "comparison");
            expectedKinds = Set.copyOf(expectedKinds);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Set<SegmentKind> PARAGRAPHS = Set.of(SegmentKind.PARAGRAPH);

    /**
     * Main body 18 — a section title 1, a subtitle 1, prose 1, two line-break runs 2, three verse lines 3, a
     * quotation 1, six table cells 6, a CDATA paragraph 1, an inline-markup paragraph 1, a note-reference
     * paragraph 1 — plus the notes body's 1. The vertical-space element, the image-only paragraph and the
     * {@code <binary>} cover yield nothing.
     */
    private static final int FB2_PRIMARY_SEGMENTS = 19;

    /**
     * Every other segment is plain text. The CDATA paragraph {@code <p><![CDATA[a < b]]></p>} is one atomic
     * protected span (1); {@code <emphasis>абзац</emphasis>} is a non-protected element with a text child, open
     * and close (2); the note-reference {@code <a l:href="#n1" type="note">1</a>} is the same shape (2). Total 5.
     */
    private static final int FB2_PRIMARY_PLACEHOLDERS = 5;

    private static final Set<SegmentKind> FB2_KINDS =
            Set.of(SegmentKind.PARAGRAPH, SegmentKind.HEADING, SegmentKind.VERSE_LINE, SegmentKind.TABLE_CELL);

    /** {@link Fb2Fixtures#NO_LANGUAGE_XML} and {@link Fb2Fixtures#EMPTY_LANGUAGE_XML} each carry one {@code <p>}. */
    private static final int FB2_MINIMAL_LANGUAGE_SEGMENTS = 1;

    /**
     * A heading 1, prose 1, six table cells 6, four list-item paragraphs 4, a block quote 1 and three trailing
     * paragraphs 3. The two code blocks, the raw-HTML block, the link-reference definition and the frontmatter
     * yield nothing and are excluded content.
     */
    private static final int MARKDOWN_PRIMARY_SEGMENTS = 16;

    /**
     * Every segment but one is plain text. The prose segment's {@code _emphasis_} (2) and reference link
     * {@code [ref][r]} (2) total 4; the hard-break paragraph's line break is 1 more. Total 5.
     */
    private static final int MARKDOWN_PRIMARY_PLACEHOLDERS = 5;

    private static final Set<SegmentKind> MARKDOWN_KINDS =
            Set.of(SegmentKind.PARAGRAPH, SegmentKind.HEADING, SegmentKind.LIST_ITEM, SegmentKind.TABLE_CELL);
    private static final Set<SegmentKind> PARAGRAPHS_AND_HEADINGS = Set.of(SegmentKind.PARAGRAPH, SegmentKind.HEADING);

    /** {@link MarkdownFixtures#MARKUP_DENSE}: a heading plus three heavily inline-marked paragraphs. */
    private static final int MARKDOWN_MARKUP_DENSE_SEGMENTS = 4;

    /**
     * The heading's strong emphasis is 2. Paragraph one: bold(2)+italic(2)+coded(1)+link(2)=7. Paragraph two:
     * Every(2)+one(2)+two(2)+three(1)+four nested strong-in-emphasis(2+2)+five link(2)+six(1)+seven(2)+eight(2)=18.
     * Paragraph three: inline code(1)+strong emphasis(2)+regular emphasis(2)+final link(2)=7. Total 2+7+18+7=34.
     */
    private static final int MARKDOWN_MARKUP_DENSE_PLACEHOLDERS = 34;

    /** {@link TxtFixtures#WINDOWS_1251}: three Cyrillic paragraphs. */
    private static final int TXT_WINDOWS_1251_SEGMENTS = 3;

    /**
     * {@link Fb2Fixtures#hazardParagraph}: the {@code <p>} owns direct text, so it is the block. CDATA is 1, the
     * note anchor {@code <a>} is 2, the text-owning {@code <div>} wrapping the nested {@code <pre>} is 2, and the
     * {@code <pre>} itself is 1 (not excluded — it sits inside an already-found block, not standalone). Total 6.
     */
    private static final int HAZARD_PARAGRAPH_PLACEHOLDERS_FB2 = 6;

    /** {@link EpubHazardFixtures#hazardParagraph}: {@code <b>}(2)+{@code <i>}(2)+comment(1)+{@code <code>}(1)=6. */
    private static final int HAZARD_PARAGRAPH_PLACEHOLDERS_EPUB = 6;

    /**
     * Every fixture the sweep exercises.
     *
     * @return the catalogue, in a stable order
     */
    public static List<Case> all() {
        final List<Case> cases = new java.util.ArrayList<>(epubCases());
        cases.addAll(otherFormatCases());
        return List.copyOf(cases);
    }

    /**
     * The EPUB family. Each fixture's rationale lives on the builder that writes it; the numbers here are the
     * independent statement of what it must produce, counted by hand from that builder's markup. Split across two
     * helpers to stay within the method-length limit rather than growing one list past it.
     */
    private static List<Case> epubCases() {
        final List<Case> cases = new java.util.ArrayList<>(epubCasesPartOne());
        cases.addAll(epubCasesPartTwo());
        return List.copyOf(cases);
    }

    private static List<Case> epubCasesPartOne() {
        return List.of(
                epub("epub/div-paragraphs", EpubFixtures::divParagraphs, 4, 0, PARAGRAPHS_AND_HEADINGS),
                epub("epub/spacer-paragraphs", EpubFixtures::spacerParagraphs, 3, 0, PARAGRAPHS),
                epub("epub/br-runs", EpubFixtures::brRuns, 4, 0, PARAGRAPHS),
                // Every paragraph here is <p><span><i>text</i></span></p>: <p> and <span> own no direct text, so
                // the descent (ADR-0027) lands the block on <i>, and the span/i wrapper never enters the segment.
                epub("epub/inline-markup", EpubFixtures::inlineMarkup, 3, 0, PARAGRAPHS),
                epub("epub/stored-entries", EpubFixtures::storedEntries, 2, 0, PARAGRAPHS),
                epub("epub/font-obfuscated", EpubFixtures::fontObfuscated, 2, 0, PARAGRAPHS),
                // The wrapped-prose div is the same wrapper shape as inline-markup above (0 tokens); the rest are
                // plain divs, br-split runs, an image-only paragraph (no segment) and an excluded <pre><code>.
                epub("epub/combination", EpubFixtures::combination, 6, 0, PARAGRAPHS),
                epub("epub/head-self-closed-script", EpubFixtures::headSelfClosedScript, 2, 0, PARAGRAPHS),
                epub("epub/head-self-closed-style", EpubFixtures::headSelfClosedStyle, 1, 0, PARAGRAPHS),
                epub("epub/head-self-closed-noscript", EpubFixtures::headSelfClosedNoscript, 1, 0, PARAGRAPHS),
                epub("epub/head-paired-script", EpubFixtures::headPairedScript, 2, 0, PARAGRAPHS));
    }

    private static List<Case> epubCasesPartTwo() {
        return List.of(
                epub("epub/body-self-closed-script", EpubFixtures::bodySelfClosedScript, 2, 0, PARAGRAPHS),
                epub("epub/script-literal-in-code-listing", EpubFixtures::scriptLiteralInCodeListing, 1, 0, PARAGRAPHS),
                epub("epub/paragraph-wrapping-division", EpubFixtures::paragraphWrappingDivision, 0, 0, Set.of()),
                // <p> owns direct text "text "; the self-closed, childless <a/> is one atomic protected span.
                epub(
                        "epub/self-closed-indexterm-anchor",
                        EpubFixtures::selfClosedIndextermAnchor,
                        2,
                        1,
                        PARAGRAPHS_AND_HEADINGS),
                // <p> owns direct text "Before "/" after"; the self-closed, childless <span/> is one atomic token.
                epub("epub/self-closed-inline-span", EpubFixtures::selfClosedInlineSpan, 2, 1, PARAGRAPHS),
                epub("epub/pre-two-leading-line-feeds", EpubFixtures::preTwoLeadingLineFeeds, 1, 0, PARAGRAPHS),
                epub("epub/pre-no-leading-line-feed", EpubFixtures::preNoLeadingLineFeed, 1, 0, PARAGRAPHS),
                epub("epub/pre-two-leading-crlf-pairs", EpubFixtures::preTwoLeadingCrLfPairs, 1, 0, PARAGRAPHS),
                epub("epub/pre-empty", EpubFixtures::preEmpty, 1, 0, PARAGRAPHS),
                epub("epub/pre-one-leading-line-feed", EpubFixtures::preOneLeadingLineFeed, 1, 0, PARAGRAPHS),
                epub(
                        "epub/hazard-paragraph",
                        EpubHazardFixtures::hazardParagraph,
                        1,
                        HAZARD_PARAGRAPH_PLACEHOLDERS_EPUB,
                        PARAGRAPHS));
    }

    /** FB2, Markdown and TXT — the three formats this change adds. */
    private static List<Case> otherFormatCases() {
        final List<Case> cases = new java.util.ArrayList<>(fb2Cases());
        cases.addAll(markdownAndTxtCases());
        return List.copyOf(cases);
    }

    /**
     * The FB2 family: the primary fixture bare and zipped, plus the two minimal fixtures task 10.2 adds — one
     * whose {@code <title-info>} omits {@code <lang>} entirely, and one whose {@code <lang>} is present but empty.
     */
    private static List<Case> fb2Cases() {
        return List.of(
                fb2("fb2/primary-windows-1251", "book.fb2", Fb2Fixtures::primary),
                fb2("fb2/primary-zipped", "book.fb2.zip", Fb2Fixtures::primaryZipped),
                fb2Minimal("fb2/no-language", "no-lang.fb2", Fb2Fixtures::noLanguage),
                fb2Minimal("fb2/empty-language", "empty-lang.fb2", Fb2Fixtures::emptyLanguage),
                new Case(
                        "fb2/hazard-paragraph",
                        BookFormat.FB2,
                        "hazard.fb2",
                        Fb2Fixtures::hazardParagraph,
                        1,
                        HAZARD_PARAGRAPH_PLACEHOLDERS_FB2,
                        PARAGRAPHS,
                        0.99,
                        Comparison.FB2_CANONICAL));
    }

    private static List<Case> markdownAndTxtCases() {
        final List<Case> cases = new java.util.ArrayList<>(markdownCases());
        cases.addAll(txtCases());
        return List.copyOf(cases);
    }

    private static List<Case> markdownCases() {
        return List.of(
                markdown(
                        "md/primary",
                        "chapter.md",
                        MarkdownFixtures::primary,
                        MARKDOWN_PRIMARY_SEGMENTS,
                        MARKDOWN_PRIMARY_PLACEHOLDERS,
                        MARKDOWN_KINDS,
                        0.95),
                markdown("md/only-a-fence", "code.md", MarkdownFixtures::onlyAFence, 0, 0, Set.of(), 0.0),
                markdown(
                        "md/markup-dense",
                        "markup-dense.md",
                        MarkdownFixtures::markupDense,
                        MARKDOWN_MARKUP_DENSE_SEGMENTS,
                        MARKDOWN_MARKUP_DENSE_PLACEHOLDERS,
                        PARAGRAPHS_AND_HEADINGS,
                        0.99));
    }

    private static List<Case> txtCases() {
        return List.of(
                new Case(
                        "txt/primary",
                        BookFormat.TXT,
                        "notes.txt",
                        TxtFixtures::primary,
                        3,
                        0,
                        PARAGRAPHS,
                        0.99,
                        Comparison.TXT_BYTES),
                new Case(
                        "txt/windows-1251",
                        BookFormat.TXT,
                        "windows-1251.txt",
                        TxtFixtures::windows1251,
                        TXT_WINDOWS_1251_SEGMENTS,
                        0,
                        PARAGRAPHS,
                        0.99,
                        Comparison.TXT_BYTES));
    }

    /** A Markdown case. The fence-only fixture declares a floor of zero because it has no translatable text. */
    private static Case markdown(
            String name,
            String fileName,
            UnaryOperator<Path> builder,
            int segments,
            int placeholders,
            Set<SegmentKind> kinds,
            double coverageFloor) {
        return new Case(
                name,
                BookFormat.MARKDOWN,
                fileName,
                builder,
                segments,
                placeholders,
                kinds,
                coverageFloor,
                Comparison.MARKDOWN_AST);
    }

    /**
     * An EPUB case. The coverage floor is 0.99 for every one of them: each fixture's only unreachable text is
     * inside a {@code <pre>} listing, which the measurement excludes from its denominator, so anything below
     * "essentially all of it" means a block was missed.
     */
    private static Case epub(
            String name, UnaryOperator<Path> builder, int segments, int placeholders, Set<SegmentKind> kinds) {
        return new Case(
                name,
                BookFormat.EPUB,
                "book.epub",
                builder,
                segments,
                placeholders,
                kinds,
                0.99,
                Comparison.EPUB_CANONICAL);
    }

    /**
     * An FB2 case over the primary fixture, bare or zipped — the same 19 segments either way, because a zip
     * wrapper is packaging and not format.
     */
    private static Case fb2(String name, String fileName, UnaryOperator<Path> builder) {
        return new Case(
                name,
                BookFormat.FB2,
                fileName,
                builder,
                FB2_PRIMARY_SEGMENTS,
                FB2_PRIMARY_PLACEHOLDERS,
                FB2_KINDS,
                0.95,
                Comparison.FB2_CANONICAL);
    }

    /**
     * An FB2 case over one of the single-paragraph, no/empty-{@code <lang>} fixtures — one segment, one word
     * "Hello world." fully reached, so the floor sits just under the measured coverage.
     */
    private static Case fb2Minimal(String name, String fileName, UnaryOperator<Path> builder) {
        return new Case(
                name,
                BookFormat.FB2,
                fileName,
                builder,
                FB2_MINIMAL_LANGUAGE_SEGMENTS,
                0,
                PARAGRAPHS,
                0.99,
                Comparison.FB2_CANONICAL);
    }
}
