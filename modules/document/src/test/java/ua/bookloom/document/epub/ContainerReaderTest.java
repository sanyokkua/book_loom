package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.RawEntry;

/**
 * {@code ContainerReader}'s {@code META-INF/container.xml} parsing (task 2.3).
 */
class ContainerReaderTest {

    @Test
    void locateOpf_validContainer_returnsTheRootfilePath() {
        final RawEntry entry = entry("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """);

        assertThat(ContainerReader.locateOpf(entry)).isEqualTo("OEBPS/content.opf");
    }

    @Test
    void locateOpf_missingEntry_isACorruptContainerFailure() {
        assertThatThrownBy(() -> ContainerReader.locateOpf(null)).isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void locateOpf_noRootfilesElement_isACorruptContainerFailure() {
        final RawEntry entry = entry("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"/>
                """);

        assertThatThrownBy(() -> ContainerReader.locateOpf(entry)).isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void locateOpf_blankFullPath_isACorruptContainerFailure() {
        final RawEntry entry = entry("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                  <rootfiles>
                    <rootfile full-path="" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """);

        assertThatThrownBy(() -> ContainerReader.locateOpf(entry)).isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void locateOpf_malformedXml_isACorruptContainerFailure() {
        final RawEntry entry = entry("not xml at all <<<");

        assertThatThrownBy(() -> ContainerReader.locateOpf(entry)).isInstanceOf(CorruptContainerException.class);
    }

    private static RawEntry entry(String xml) {
        return new RawEntry("META-INF/container.xml", 0, 0, xml.getBytes(StandardCharsets.UTF_8));
    }
}
