package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;

/**
 * A term removed from the recurring terms is not added back by the next scan (15i.N8), as a removed glossary name is
 * not: the person's removal, and a model's drop, hold for the session until the term is stored again.
 */
class LexiconRemovalMemoryTest {

    private final NamesPassFixture fixture = new NamesPassFixture();
    private LexiconService service;
    private NamesPassModel model;

    @BeforeEach
    void setUp() {
        fixture.open(NamesPassFixture.brief("uk", NamePolicy.TRANSLITERATE));
        service = fixture.injector.getInstance(LexiconService.class);
        model = new NamesPassModel().usesWord("beacon", "term");
    }

    private boolean holdsBeacon() {
        return fixture.lexiconNow().stream().map(LexiconEntry::term).anyMatch("beacon"::equals);
    }

    // The control: with nothing removed, the model keeps the word and the scan adds it.
    @Test
    void scanWithModel_wordTheModelKeeps_isAdded() {
        service.scanWithModel(PROJECT, model, event -> {});

        assertThat(holdsBeacon()).isTrue();
    }

    // IF the removal were not remembered, THEN the person's removal would be undone by the next Scan.
    @Test
    void scanWithModel_wordThePersonRemoved_isNotAddedAgain() {
        service.add(PROJECT, "beacon");
        service.remove(PROJECT, "beacon");

        service.scanWithModel(PROJECT, model, event -> {});

        assertThat(holdsBeacon()).isFalse();
    }

    // IF a model's drop were forgotten, THEN the same word would come back at every scan and be dropped again.
    @Test
    void scanWithModel_wordTheModelDroppedInAReview_isNotAddedAgain() {
        fixture.lexicon.put(LexiconEntry.of(PROJECT, "beacon"));
        service.review(PROJECT, new NamesPassModel().usesWord("beacon", "word"), event -> {});
        assertThat(holdsBeacon()).isFalse();

        service.scanWithModel(PROJECT, model, event -> {});

        assertThat(holdsBeacon()).isFalse();
    }

    @Test
    void add_wordThePersonRemovedEarlier_isHeldAgain() {
        service.add(PROJECT, "beacon");
        service.remove(PROJECT, "beacon");

        final var added = service.add(PROJECT, "beacon");

        assertThat(added.isOk()).isTrue();
        assertThat(Objects.requireNonNull(
                        fixture.lexicon.wasRemoved(PROJECT, "beacon").data()))
                .isFalse();
    }
}
