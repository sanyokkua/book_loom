package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.inject.Guice;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;

/**
 * The name scan on the real book the owner's run exposed the junk with. The book is not in the repository; on a
 * checkout without the owner's local examples this test is skipped.
 */
class BartimaeusBookScanTest {

    private static final Path BOOK = Path.of(
            "../../.temporary_context/Books_Examples/Jonathan Stroud - Bartimaeus 01 - The Amulet of Samarkand.epub");

    @Test
    void candidates_realBook_proposesNamesAndNoCommonWords() {
        assumeTrue(Files.isRegularFile(BOOK), "the owner's local example book is not present");
        final Result<Document> opened = Guice.createInjector(new DocumentModule())
                .getInstance(DocumentPort.class)
                .open(BOOK);
        final List<Segment> body = Objects.requireNonNull(opened.data(), "opened").units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .toList();

        final List<String> terms = FrequencyScan.candidates(body, FrequencyScan.PROPOSAL_MIN_COUNT, "en").stream()
                .map(NameCandidate::term)
                .toList();

        assertThat(terms)
                .contains("Nathaniel", "Bartimaeus", "Lovelace", "Underwood", "Simon Lovelace", "Sholto Pinn")
                .doesNotContain("Well", "Don", "Words", "Seal", "Shield", "Al", "Eastern", "Shields", "Simon", "I’m");
    }
}
