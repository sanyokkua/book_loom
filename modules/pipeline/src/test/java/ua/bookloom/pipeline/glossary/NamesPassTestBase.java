package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;

/**
 * What the names pass tests share: the invented book, the stored tables, a model that answers by response format and
 * looks at the stored tables before every call.
 */
abstract class NamesPassTestBase {

    static final Duration PER_CALL = Duration.ofSeconds(2);

    final NamesPassFixture fixture = new NamesPassFixture();
    final List<List<GlossaryEntry>> glossarySeen = new ArrayList<>();
    final List<List<LexiconEntry>> lexiconSeen = new ArrayList<>();
    final List<JobEvent> events = new ArrayList<>();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    /** The model of the invented book: what each name is, which words are terms, what each term is rendered as. */
    NamesPassModel model() {
        return new NamesPassModel()
                .reads("Brann", "person", false, false)
                .reads("Wren", "person", false, false)
                .reads("Harrowgate", "place", false, false)
                .reads("Zorbo", "thing", false, true)
                .reads("Silverbrook", "place", true, false)
                .callsNotAName("Overture")
                .usesWord("beacon", "term")
                .usesWord("wardstone", "name")
                .suggests("Brann", "Бранн")
                .suggests("Wren", "Врен")
                .suggests("Harrowgate", "Гарроугейт")
                .suggests("Zorbo", "Zorbo")
                .suggests("Silverbrook", "Срібний Струмок")
                .suggests("Marrow Lane", "Інша вулиця")
                .suggests("Gatewarden", "Хтось інший")
                .suggests("wardstone", "вартовий камінь")
                .suggests("beacon", "маяк")
                .beforeEachCall(this::lookAtTheTables);
    }

    // What the tables hold when a call goes out, and the clock moved by the call that follows.
    void lookAtTheTables() {
        glossarySeen.add(fixture.glossaryNow());
        lexiconSeen.add(fixture.lexiconNow());
        fixture.clock.advance(PER_CALL);
    }

    void seeded(final String target, final NamePolicy policy) {
        fixture.open(NamesPassFixture.brief(target, policy));
        fixture.seedOpenNames().seedPersonsEntries().seedTerms();
    }

    Result<NamesPassReport> run(final NamesPassModel model) {
        return fixture.pass().run(PROJECT, model, null, events::add);
    }

    NamesPassReport succeeded(final Result<NamesPassReport> result) {
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    GlossaryEntry held(final String term) {
        return fixture.glossaryNow().stream()
                .filter(entry -> entry.term().equals(term))
                .findFirst()
                .orElseThrow();
    }
}
