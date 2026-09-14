package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code Document}'s construction-time invariants.
 */
class DocumentTest {

    private static Document epub() {
        return new Document("book-1", BookFormat.EPUB, null, null, null, null, "hash", Map.of(), List.of());
    }

    @Test
    void units_returned_isUnmodifiable() {
        final Document document = epub();

        assertThatThrownBy(() -> document.units().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metadata_returned_isUnmodifiable() {
        final Document document = epub();

        assertThatThrownBy(() -> document.metadata().put("title", "New Title"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withUnits_newList_changesOnlyUnits() {
        final Unit originalUnit =
                new Unit("unit", 0, "unit.md", "text/markdown", new SkeletonHandle("skeleton-1"), List.of());
        final Document original = new Document(
                "book-1",
                BookFormat.MARKDOWN,
                "en",
                "en",
                "UTF-8",
                false,
                "hash",
                Map.of("title", "Book"),
                List.of(originalUnit));

        final Unit replacement =
                new Unit("unit-2", 0, "unit-2.md", "text/markdown", new SkeletonHandle("skeleton-2"), List.of());
        final Document updated = original.withUnits(List.of(replacement));

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.format()).isEqualTo(original.format());
        assertThat(updated.declaredLang()).isEqualTo(original.declaredLang());
        assertThat(updated.detectedSourceLang()).isEqualTo(original.detectedSourceLang());
        assertThat(updated.charset()).isEqualTo(original.charset());
        assertThat(updated.hasBom()).isEqualTo(original.hasBom());
        assertThat(updated.contentHash()).isEqualTo(original.contentHash());
        assertThat(updated.metadata()).isEqualTo(original.metadata());
        assertThat(updated.units()).containsExactly(replacement);
        assertThat(original.units()).containsExactly(originalUnit);
    }

    /**
     * A container format leaves both encoding components unset, and the record must carry that through rather
     * than substituting a default — an invented {@code UTF-8} here would be indistinguishable from a real one.
     */
    @Test
    void constructor_nullCharsetAndBom_roundTripAsNull() {
        final Document document = epub();

        assertThat(document.charset()).isNull();
        assertThat(document.hasBom()).isNull();
    }

    @Test
    void constructor_recordedCharsetAndBom_areReadBack() {
        final Document document =
                new Document("book-1", BookFormat.TXT, null, null, "windows-1251", true, "hash", Map.of(), List.of());

        assertThat(document.charset()).isEqualTo("windows-1251");
        assertThat(document.hasBom()).isTrue();
    }

    // Suppressed deliberately: NullAway forbids this call statically, which is exactly why the runtime guard must
    // still exist and be proven for a caller reaching this record without that analysis.
    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullId_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new Document(null, BookFormat.EPUB, null, null, null, null, "hash", Map.of(), List.of()));
    }

    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullFormat_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Document("book-1", null, null, null, null, null, "hash", Map.of(), List.of()));
    }
}
