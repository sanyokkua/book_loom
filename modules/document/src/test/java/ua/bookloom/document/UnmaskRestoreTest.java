package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/**
 * {@link DocumentService#unmask}'s substitution once the placeholder gate passes (task group 7.11), and the
 * escaping it applies to whatever the model wrote around the tokens (task group 7.12's non-filesystem cases) —
 * driven through the port against hand-built EPUB-kind segments. {@link UnmaskPlaceholderGateTest} owns the gate
 * itself; {@link UnmaskFb2DocumentTest} owns the cases that need a real parsed document.
 */
class UnmaskRestoreTest {

    // WHEN the multiset comparison passes, the system SHALL replace each ⟦gN⟧ token with the
    // exact fragment recorded for it and leave every other character as written.
    @Test
    void unmask_translatedParagraph_restoresEmphasisAroundTheTranslatedWords() {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "<em>");
        placeholders.put("g1", "</em>");
        final Segment segment = segment("He opened the ⟦g0⟧old⟦g1⟧ door.", placeholders);

        final Result<String> result =
                newService().unmask(BookFormat.EPUB, segment, "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.");

        assertThat(result.data()).isEqualTo("Він відчинив <em>старі</em> двері.");
    }

    // WHEN the multiset comparison passes, the system SHALL replace each ⟦gN⟧ token with the
    // exact fragment recorded for it and leave every other character as written.
    @Test
    void unmask_atomicCodeSpan_comesBackByteForByte() {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "<code>List.of()</code>");
        final Segment segment = segment("Call ⟦g0⟧ first.", placeholders);

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Спочатку викличте ⟦g0⟧.");

        assertThat(result.data()).isEqualTo("Спочатку викличте <code>List.of()</code>.");
    }

    // WHEN the multiset comparison passes, the system SHALL replace each ⟦gN⟧ token with the
    // exact fragment recorded for it and leave every other character as written.
    @Test
    void unmask_movedToken_restoresAtItsNewPosition() {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "<em>");
        placeholders.put("g1", "</em>");
        final Segment segment = segment("⟦g0⟧old⟦g1⟧ door", placeholders);

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "двері ⟦g0⟧старі⟦g1⟧");

        assertThat(result.data()).isEqualTo("двері <em>старі</em>");
    }

    // WHEN the multiset comparison passes, the system SHALL replace each ⟦gN⟧ token with the
    // exact fragment recorded for it and leave every other character as written.
    @Test
    void unmask_tokenSpelledInsideARestoredFragment_isNotSubstitutedAgain() {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "<code>⟦g0⟧</code>");
        final Segment segment = segment("Type ⟦g0⟧ exactly.", placeholders);

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Введіть ⟦g0⟧ точно.");

        assertThat(result.data()).isEqualTo("Введіть <code>⟦g0⟧</code> точно.");
    }

    // IF a target contains a character significant in its format's markup, THEN the restored
    // content SHALL carry it as escaped character data and the operation SHALL NOT fail.
    @Test
    void unmask_bareLessThanSignFromTheModel_isWrittenAsEscapedCharacterData() {
        final Segment segment = segment("Compare them.", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Якщо x < y");

        assertThat(result.data()).isEqualTo("Якщо x &lt; y");
    }

    // IF a target contains a character significant in its format's markup, THEN the restored
    // content SHALL carry it as escaped character data and the operation SHALL NOT fail.
    @Test
    void unmask_bareAmpersandFromTheModel_isWrittenAsEscapedCharacterData() {
        final Segment segment = segment("Smith & Sons", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Сміт & Сини");

        assertThat(result.data()).isEqualTo("Сміт &amp; Сини");
    }

    // IF a target contains a character significant in its format's markup, THEN the restored
    // content SHALL carry it as escaped character data and the operation SHALL NOT fail.
    @Test
    void unmask_textShapedLikeATag_isWrittenAsEscapedCharacterDataNotAnElement() {
        final Segment segment = segment("Read it.", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Прочитай <це>");

        assertThat(result.data()).isEqualTo("Прочитай &lt;це&gt;");
    }

    /** A minimal EPUB-kind segment carrying {@code masked} and {@code placeholders}; every other field is a stand-in. */
    // IF a target contains a character significant in its format's markup, THEN the restored
    // content SHALL carry it as escaped character data and the operation SHALL NOT fail.
    // The plain-text arm of that rule is the identity: TXT has no markup to protect, so a character that would be
    // escaped for EPUB or FB2 must come back exactly as the model wrote it.
    @ParameterizedTest
    @ValueSource(strings = {"Якщо x < y", "Сміт & Сини", "Прочитай <це>", "a ]]> b"})
    void unmask_txtTargetWithMarkupSignificantCharacters_restoresThemUnescaped(String target) {
        final Segment segment = segment("Compare them.", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.TXT, segment, target);

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).isEqualTo(target);
    }

    private static Segment segment(String masked, Map<String, String> placeholders) {
        return new Segment(
                "seg-1",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                placeholders,
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
