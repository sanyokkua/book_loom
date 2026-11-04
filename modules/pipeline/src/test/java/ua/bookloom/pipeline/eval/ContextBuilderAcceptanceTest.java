package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.WireMockProvider;

/**
 * Group 11's acceptance (design D8, D24): a call names only what its window holds, and a draft and its repair share
 * their system bytes. Written red in task 11.1.
 */
@Tag("slow")
class ContextBuilderAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int BATCH_SIZE = 4;
    private static final String ITEMS_MARK = "<Items>";
    private static final String REPAIR_MARK = "[Defects to fix";

    @TempDir
    Path directory;

    @Test
    void windowInjection_recordedMiniEnRun_zeroEntriesOutsideWindow() {
        // The grader reads every recorded call of a Balanced mini-en run on the pseudo model.
        final MiniBook book = MiniBook.load(MiniBook.MINI_EN);
        final MiniRun run = new MiniRunner(book, QualityDial.BALANCED, NamePolicy.TRANSLITERATE, null)
                .run(new PseudoChatModel(MAPPER), directory);

        final MiniScorer.Verdict verdict = MiniGraders.windowInjection(
                run.sent().stream().map(CaseRecord.Exchange::of).toList(),
                book.blocks().stream().map(MiniBook.Block::text).toList(),
                MiniGold.load(MiniBook.MINI_EN));

        assertThat(verdict.detail()).as("map entries were checked").doesNotContain("mapChecked=0 ");
        assertThat(verdict.detail())
                .as("block-8 entries were checked (the card exists)")
                .doesNotContain("block8Checked=0");
        assertThat(verdict.ok()).as(verdict.detail()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void systemBytes_batchDraftAndItsRepair_identical(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.reply(batchReply(), Duration.ZERO),
                    provider.reply("{\"results\":[]}", Duration.ZERO),
                    provider.target("Вона сказала, що ключ у шухляді.", Duration.ZERO)));
            final Prepared prepared = SequenceJobs.importBook(
                    TestBooks.markdown(directory.resolve("Book.md"), firstParagraphs()),
                    SequenceJobs.brief(
                            "ru",
                            "uk",
                            QualityDial.BALANCED,
                            new Narrator(NarratorPerson.UNSPECIFIED, Gender.UNKNOWN)));

            SequenceJobs.run(
                    prepared, provider.model(REQUEST_TIMEOUT, ignored -> {}), ReviewMode.UNATTENDED, null, e -> {});

            final List<String> bodies = provider.chatBodies();
            final String draft = firstContaining(bodies, ITEMS_MARK);
            final String repair = firstContaining(bodies, REPAIR_MARK);
            assertThat(repair).as("a repair request was sent after the batch").isNotEmpty();
            assertThat(systemOf(repair)).isNotEmpty().isEqualTo(systemOf(draft));
        }
    }

    private static String firstParagraphs() {
        return MiniBook.load(MiniBook.MINI_RU).blocks().stream()
                        .filter(b -> b.kind() == MiniBook.Kind.P)
                        .limit(BATCH_SIZE)
                        .map(MiniBook.Block::text)
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElseThrow()
                + "\n";
    }

    private static String batchReply() {
        return "{\"items\":["
                + "{\"id\":\"1\",\"target\":\"Я повернулася пізно.\"},"
                + "{\"id\":\"2\",\"target\":\"— Поклич Веломира, — сказала вона.\"},"
                + "{\"id\":\"3\",\"target\":\"Вона сказала, что ключ у шухляді.\"},"
                + "{\"id\":\"4\",\"target\":\"— Двері були відчинені, — тихо сказав він.\"}"
                + "]}";
    }

    private static String firstContaining(final List<String> bodies, final String mark) {
        return bodies.stream().filter(b -> b.contains(mark)).findFirst().orElse("");
    }

    private static String systemOf(final String body) {
        if (body.isEmpty()) {
            return "";
        }
        try {
            final JsonNode messages = MAPPER.readTree(body).path("messages");
            return Stream.iterate(0, i -> i + 1)
                    .limit(messages.size())
                    .map(messages::get)
                    .filter(m -> "system".equals(m.path("role").asText()))
                    .map(m -> m.path("content").asText())
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
