package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.llm.ChatModelFactoryImpl;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The automatic names pass of Names &amp; style, run through the real injector over an invented book with the pseudo
 * model, and undone: Undo all must leave the glossary and the recurring terms exactly as they were before the pass,
 * the person's own locked entry included.
 *
 * <p>Every unit test of the results dialog uses scripted services; this is the one that puts the real glossary
 * service, the real lexicon service, the real pass and the real reverts together (15i.U6).
 */
// `slow`: a pass over a generated book end to end; `test` and the gate run it, the `fastTest` inner loop leaves it out.
@Tag("slow")
class NamesPassUndoEndToEndTest extends WorkspaceTestBase {

    private static final List<String> PEOPLE_DEEDS = List.of(
            "climbed the hill", "crossed the field", "entered the inn", "studied the sky", "followed the river");

    // The person's own work: a woman's gender chosen by the person and a locked office with its target.
    private static final String WREN_ID = "seed-wren";
    private static final String GATE_ID = "seed-gatewarden";

    @Test
    void undoAll_afterThePseudoModelPass_leavesBothTablesAsTheyWereBeforeIt() throws Exception {
        final Path book = Files.writeString(booksDir.resolve("Story.txt"), body(), StandardCharsets.UTF_8);
        startWorkspace(ReviewMode.UNATTENDED, new TargetingPseudoModule());
        openBook(book, paragraphs());
        chooseBrief("uk", booksDir.resolve("Story.uk.txt"));
        chooseModel();
        final String projectId = Objects.requireNonNull(onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .projectId();
        seedThePersonsWork(projectId);
        final List<GlossaryEntry> namesBefore = namesNow(projectId);
        final List<LexiconEntry> termsBefore = termsNow(projectId);
        final NamesStyleViewModel names = injector.getInstance(NamesStyleViewModel.class);
        onFx(() -> names.show(projectId));
        waitUntil(() -> names.rows().size() == namesBefore.size());

        onFx(names::autoPrepare);
        waitUntil(() -> !names.busy().get() && names.results().get() != null);
        final ChangeResults results = onFx(() -> names.results().get());

        assertThat(results.outcome()).isEqualTo(ChangeResults.Outcome.CHANGED);
        assertThat(onFx(() -> Objects.requireNonNull(names.notice().get()).text())).startsWith("Names prepared");
        assertThat(namesNow(projectId)).isNotEqualTo(namesBefore);
        assertThat(entry(namesNow(projectId), GATE_ID)).isEqualTo(entry(namesBefore, GATE_ID));

        onFx(results::undoAll);
        waitUntil(() -> !results.working().get() && !results.hasRowsToRevert());

        assertThat(namesNow(projectId)).isEqualTo(namesBefore);
        assertThat(termsNow(projectId)).isEqualTo(termsBefore);
        assertThat(onFx(() -> List.copyOf(names.rows()))).containsExactlyInAnyOrderElementsOf(namesBefore);
        // IF the pass's notice stayed, THEN it would still claim names were prepared after all of them were undone.
        assertThat(onFx(() -> Objects.requireNonNull(names.notice().get()).text()))
                .isEqualTo("Undone: Prepare names automatically · every change it made is put back");
    }

    /**
     * The pseudo model, which suggests no target for any name, but for the invented names of this book: the one
     * difference from the production pseudo model, so that the pass has something to change.
     */
    private static final class TargetingPseudoModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(ChatModelFactory.class).to(TargetingFactory.class);
        }
    }

    private static final class TargetingFactory implements ChatModelFactory {

        private final ChatModelFactoryImpl real;

        @Inject
        TargetingFactory(final ChatModelFactoryImpl real) {
            this.real = real;
        }

        @Override
        public Result<ChatModel> create(final ModelSelection selection) {
            return real.create(selection).map(pseudo -> new TargetingPseudo(pseudo));
        }
    }

    private static final class TargetingPseudo implements ChatModel {

        private static final Map<String, String> TARGETS = Map.of(
                "Brann", "Бранн",
                "Harrowgate", "Гарроугейт",
                "beacon", "маяк",
                "wardstone", "вартовий камінь",
                "gantry", "кран");
        private static final Pattern LISTED = Pattern.compile("(?m)^- (.+?) — ");

        private final ChatModel pseudo;

        TargetingPseudo(final ChatModel pseudo) {
            this.pseudo = pseudo;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            final ResponseFormat format = request.responseFormat();
            if (format == null || !"suggest-targets".equals(format.name())) {
                return pseudo.chat(request);
            }
            final String asked = request.messages().getLast().content();
            final Matcher listed = LISTED.matcher(asked);
            final StringBuilder reply = new StringBuilder("{\"suggestions\":[");
            String separator = "";
            while (listed.find()) {
                final String target = TARGETS.getOrDefault(listed.group(1), "");
                reply.append(separator)
                        .append("{\"term\":\"")
                        .append(listed.group(1))
                        .append("\",\"target\":\"")
                        .append(target)
                        .append("\",\"gender\":\"unknown\"}");
                separator = ",";
            }
            return Result.ok(new ChatResponse(reply.append("]}").toString(), FinishReason.STOP));
        }
    }

    private void seedThePersonsWork(final String projectId) {
        final GlossaryService glossary = injector.getInstance(GlossaryService.class);
        assertThat(glossary.add(new GlossaryEntry(
                                WREN_ID, projectId, "Wren", null, TermType.CHARACTER, Gender.FEMALE, false))
                        .isOk())
                .isTrue();
        assertThat(glossary.add(new GlossaryEntry(
                                GATE_ID, projectId, "Gatewarden", "Брамник", TermType.TERM, Gender.UNKNOWN, true))
                        .isOk())
                .isTrue();
        Stream.of("Brann", "Harrowgate", "Overture")
                .forEach(term -> assertThat(glossary.add(new GlossaryEntry(
                                        "seed-" + term, projectId, term, null, TermType.OTHER, Gender.UNKNOWN, false))
                                .isOk())
                        .isTrue());
        final LexiconRepository lexicon = injector.getInstance(LexiconRepository.class);
        lexicon.put(LexiconEntry.of(projectId, "ferry"));
        lexicon.put(LexiconEntry.of(projectId, "anvil").withChosen("ковадло"));
    }

    private List<GlossaryEntry> namesNow(final String projectId) {
        return Objects.requireNonNull(injector.getInstance(GlossaryRepository.class)
                        .all(projectId)
                        .data())
                .stream()
                .sorted(Comparator.comparing(GlossaryEntry::term))
                .toList();
    }

    private List<LexiconEntry> termsNow(final String projectId) {
        return Objects.requireNonNull(injector.getInstance(LexiconRepository.class)
                        .all(projectId)
                        .data())
                .stream()
                .sorted(Comparator.comparing(LexiconEntry::term))
                .toList();
    }

    private static GlossaryEntry entry(final List<GlossaryEntry> entries, final String id) {
        return entries.stream().filter(held -> held.id().equals(id)).findFirst().orElseThrow();
    }

    private static int paragraphs() {
        return lines().size();
    }

    private static String body() {
        return String.join("\n\n", lines()) + "\n";
    }

    // Invented: two people each followed by a pronoun, a place, a brand, a street, a locked office, a word that is no
    // name, and common words that recur.
    private static List<String> lines() {
        final List<String> lines = new ArrayList<>();
        PEOPLE_DEEDS.forEach(deed -> lines.add("So Brann " + deed + ". He did not pause."));
        PEOPLE_DEEDS.forEach(deed -> lines.add("So Wren " + deed + ". She did not pause."));
        Stream.of(
                        "So they reached Harrowgate at dusk.",
                        "And rain fell over Harrowgate all night.",
                        "So a bell rang in Harrowgate.",
                        "And nobody left Harrowgate before spring.",
                        "So a bottle of Zorbo stood on the shelf.",
                        "And a crate of Zorbo waited by the door.",
                        "So a poster for Zorbo hung in the window.",
                        "And an empty tin of Zorbo rolled away.",
                        "So the Overture began.",
                        "And the Overture ended.",
                        "So the Gatewarden nodded at the stranger.",
                        "And the Gatewarden opened the bolt.",
                        "So the Gatewarden shut the hatch.",
                        "And the Gatewarden lit a lantern.")
                .forEach(lines::add);
        Stream.of("beacon", "ferry", "wardstone", "anvil", "gantry")
                .forEach(word -> IntStream.range(0, 6)
                        .mapToObj(index -> "And the " + word + " " + deedOf(index) + ".")
                        .forEach(lines::add));
        return List.copyOf(lines);
    }

    private static String deedOf(final int index) {
        return List.of("burned", "rattled", "creaked", "gleamed", "stood", "shook")
                .get(index);
    }
}
