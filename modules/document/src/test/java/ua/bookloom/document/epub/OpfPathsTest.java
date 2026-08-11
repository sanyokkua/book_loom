package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code OpfPaths}'s href resolution against the OPF's own location.
 */
class OpfPathsTest {

    @Test
    void parentOf_opfNestedInDirectory_returnsThatDirectory() {
        assertThat(OpfPaths.parentOf("OEBPS/content.opf")).isEqualTo("OEBPS/");
    }

    @Test
    void parentOf_opfAtArchiveRoot_returnsEmpty() {
        assertThat(OpfPaths.parentOf("content.opf")).isEmpty();
    }

    @Test
    void resolve_relativeHref_isJoinedToOpfDirectory() {
        assertThat(OpfPaths.resolve("OEBPS/", "chapter01.xhtml")).isEqualTo("OEBPS/chapter01.xhtml");
    }

    @Test
    void resolve_hrefWithFragment_stripsTheFragment() {
        assertThat(OpfPaths.resolve("OEBPS/", "notes.xhtml#note1")).isEqualTo("OEBPS/notes.xhtml");
    }

    @Test
    void resolve_archiveAbsoluteHref_isNotJoinedToOpfDirectory() {
        assertThat(OpfPaths.resolve("OEBPS/", "/images/cover.jpg")).isEqualTo("images/cover.jpg");
    }
}
