package ua.bookloom.document.fb2;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.detect.CharsetLadder;
import ua.bookloom.document.model.CorruptContainerException;

/**
 * Reads an FB2's XML declaration and adjudicates it against what the bytes actually contain (EC-FB2-2).
 *
 * <p><strong>Why the check cannot be a decode attempt.</strong> The encodings real books misdeclare —
 * {@code iso-8859-1}, {@code windows-1252} — accept every possible byte, so decoding always succeeds and always
 * produces nonsense. The declaration is therefore compared against ICU's independent detection.
 *
 * <p><strong>Why detection runs over the prose and not over the file.</strong> Measured on the primary fixture,
 * ICU reads the whole FB2 document as {@code ISO-8859-1} at confidence 26 — because an FB2 file is mostly ASCII
 * markup plus a base64 cover image, and the Cyrillic that carries the signal is a minority of the bytes. Over the
 * document's text alone, with markup and binary payloads removed, the same detector is decisive. Checking the raw
 * file would therefore have produced a check that never fires — which looks like a passing test and is a book
 * silently imported as mojibake.
 *
 * <p><strong>Why disagreement refuses rather than substitutes.</strong> The frozen edge case binds one thing:
 * never corrupt silently. A book decoded under the wrong charset is not visibly broken — it imports, it segments,
 * it translates, and the damage surfaces in the finished file after the whole run has been paid for.
 *
 * <p><strong>Why a name mismatch alone is not enough.</strong> Two single-byte code pages that make the same text
 * of these bytes disagree about nothing that matters, whatever they are called; the comparison is therefore over
 * the decoded text, not over the charset names.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Fb2Encoding {

    /**
     * How far ICU must prefer another charset over the declared one before the declaration is called
     * contradicted, in ICU's [0,100] confidence.
     *
     * <p>A <em>margin</em> rather than an absolute threshold, because an absolute threshold cannot express this
     * question. Measured on the primary fixture's prose, ICU's best match scores 19 — a real answer, and nowhere
     * near any threshold one might call "confident". What separates a correct declaration from a wrong one is not
     * the winner's score but the gap: for a correctly declared {@code windows-1251} book the declared charset
     * <em>is</em> the winner; for the same bytes declared {@code windows-1252} the declared charset is absent
     * from ICU's candidate list entirely, and declared {@code iso-8859-1} scores 3 against that 19.
     *
     * <p>The margin is what keeps this from refusing good books. Two Cyrillic code pages ICU rates within a few
     * points of each other are not a contradiction worth refusing over — and a false refusal is total, which is
     * the lesson ADR-0026 was written from.
     */
    private static final int DECISIVE_PREFERENCE_MARGIN = 10;

    /** Below this much prose there is nothing for a statistical detector to work with, so it abstains. */
    private static final int MINIMUM_PROSE_BYTES = 64;

    private static final int DECLARATION_PROBE_BYTES = 256;
    private static final Pattern ENCODING_DECLARATION = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final String BINARY_ELEMENT = "binary";

    /**
     * The encoding name the XML declaration spells, exactly as written.
     *
     * @param fileBytes the FB2 document's raw bytes
     * @return the declared encoding name, or {@code null} when the document declares none
     */
    static @Nullable String declaredEncodingName(byte[] fileBytes) {
        final int probeLength = Math.min(fileBytes.length, DECLARATION_PROBE_BYTES);
        final String prolog = new String(fileBytes, 0, probeLength, StandardCharsets.US_ASCII);
        final Matcher matcher = ENCODING_DECLARATION.matcher(prolog);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Refuses the book when its declaration confidently disagrees with its prose.
     *
     * <p>Runs after parsing and before any {@code Document}, {@code Unit} or {@code Segment} exists, so refusing
     * here is still a refusal rather than a partial import — a tree in memory is not an imported book.
     *
     * @param tree the parsed document, decoded with {@code declared}
     * @param declared the charset the declaration resolved to
     * @throws CorruptContainerException if detection confidently reads the prose as a charset that makes
     *     different text of it than the declaration does
     */
    static void refuseIfDeclarationContradictsContent(org.jdom2.Document tree, Charset declared) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(declared, "declared");
        final byte[] proseBytes = proseBytes(tree, declared);
        if (proseBytes.length < MINIMUM_PROSE_BYTES) {
            return;
        }
        final List<CharsetLadder.Detection> candidates = CharsetLadder.detectAll(proseBytes);
        if (candidates.isEmpty()) {
            return;
        }
        final CharsetLadder.Detection best = candidates.get(0);
        if (declared.equals(best.charset()) || decodesIdentically(proseBytes, declared, best.charset())) {
            return;
        }
        if (best.confidence() - confidenceOf(candidates, declared) < DECISIVE_PREFERENCE_MARGIN) {
            return;
        }
        throw new CorruptContainerException("Declared encoding " + declared.name() + " contradicts the document's "
                + "content, which reads as " + best.charset().name());
    }

    /** How credible ICU finds the declared charset; a charset it does not list at all scores zero. */
    private static int confidenceOf(List<CharsetLadder.Detection> candidates, Charset declared) {
        for (final CharsetLadder.Detection candidate : candidates) {
            if (declared.equals(candidate.charset())) {
                return candidate.confidence();
            }
        }
        return 0;
    }

    /**
     * The document's prose, re-encoded with the charset it was decoded under — which reproduces exactly the
     * source bytes of that text, so the detector sees the original evidence with the markup taken away.
     * {@code <binary>} payloads are excluded: a base64 cover image is a long run of ASCII that tells a
     * statistical detector nothing except "this file is mostly Latin".
     */
    private static byte[] proseBytes(org.jdom2.Document tree, Charset declared) {
        final StringBuilder prose = new StringBuilder();
        appendProse(tree.getRootElement(), prose);
        return prose.toString().getBytes(declared);
    }

    private static void appendProse(Element element, StringBuilder prose) {
        if (BINARY_ELEMENT.equals(element.getName())) {
            return;
        }
        for (final Content child : element.getContent()) {
            appendChildProse(child, prose);
        }
    }

    private static void appendChildProse(Content child, StringBuilder prose) {
        if (child instanceof Text text) {
            prose.append(text.getText());
        } else if (child instanceof Element childElement) {
            appendProse(childElement, prose);
        }
    }

    /** Whether two charsets make the same text of these bytes. */
    private static boolean decodesIdentically(byte[] proseBytes, Charset left, Charset right) {
        return new String(proseBytes, left).equals(new String(proseBytes, right));
    }
}
