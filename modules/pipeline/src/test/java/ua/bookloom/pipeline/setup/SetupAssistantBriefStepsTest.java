package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SampleAnswer;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The request steps the brief suggestion announces to its progress dialog: what the model knows of the book, one per
 * window of the story, and the synthesis, each counted in the plan before the first is sent (the planned total is the
 * calls the suggestion can make, so a skipped one ends with no call).
 */
class SetupAssistantBriefStepsTest extends SetupAssistantBriefTestBase {

    private static final String EMPTY_WINDOW = "{\"genre\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"register\":{\"evidence\":\"\",\"value\":\"neutral\",\"confidence\":0.5},"
            + "\"voice\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"audience\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"narrator\":{\"evidence\":\"\",\"value\":\"unspecified\",\"confidence\":0.5},"
            + "\"narratorGender\":{\"evidence\":\"\",\"value\":\"unknown\",\"name\":\"\",\"confidence\":0.5}}";

    private final List<JobEvent> events = new ArrayList<>();

    private void dialIs(final QualityDial dial) {
        final Project stored = Objects.requireNonNull(
                        desk.projects().find(desk.projectId()).data())
                .orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withDial(dial)));
    }

    // The file is Book.md, so a book titled "Book" has a title that is only its file stem.
    private void bookTitled(final String front, final String story) {
        desk = ReviewFixtures.markdown(tempDir, front + story);
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    private Result<BriefSuggestion> suggest() {
        return assistant.suggestBrief(desk.projectId(), model, null, events::add);
    }

    private List<StepsPlanned> plans() {
        return events.stream()
                .filter(StepsPlanned.class::isInstance)
                .map(StepsPlanned.class::cast)
                .filter(plan -> plan.kind() == StepKind.REQUEST)
                .toList();
    }

    private List<StepChanged> requests(final StepState state) {
        return events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filter(change -> change.kind() == StepKind.REQUEST && change.state() == state)
                .toList();
    }

    private List<String> startedLabels() {
        return requests(StepState.STARTED).stream().map(StepChanged::label).toList();
    }

    private void windowsRead(final int windows, final String reply) {
        windowsAnswer(IntStream.range(0, windows).mapToObj(index -> reply).toArray(String[]::new));
    }

    // Fast reads three windows: the known title adds one knowledge request and the synthesis one more, five in all.
    @Test
    void suggestBrief_fastKnownTitle_plansKnowledgeThreeSamplesSynthesis() {
        dialIs(QualityDial.FAST);
        knowledgeAnswers(KNOWN);
        windowsRead(3, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(plans()).containsExactly(new StepsPlanned(StepKind.REQUEST, 5, false));
        assertThat(startedLabels()).containsExactly("knowledge", "sample-1", "sample-2", "sample-3", "synthesis");
        assertThat(requests(StepState.ENDED))
                .extracting(StepChanged::result)
                .containsOnly(StepResult.DONE)
                .hasSize(5);
        assertThat(requests(StepState.STARTED)).extracting(StepChanged::index).containsExactly(0, 1, 2, 3, 4);
    }

    // Balanced (the default) reads four windows: knowledge, four samples and the synthesis, six steps in all.
    @Test
    void suggestBrief_balancedKnownTitle_plansKnowledgeFourSamplesSynthesis() {
        knowledgeAnswers(KNOWN);
        windowsRead(WINDOWS, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(plans()).containsExactly(new StepsPlanned(StepKind.REQUEST, 6, false));
        assertThat(startedLabels())
                .containsExactly("knowledge", "sample-1", "sample-2", "sample-3", "sample-4", "synthesis");
    }

    // Each sample step names the window's place in the book, and the answer event carries the same place and the text
    // sent.
    @Test
    void suggestBrief_sampleSteps_carryTheWindowLocationAndTheSentText() {
        dialIs(QualityDial.FAST);
        knowledgeAnswers(KNOWN);
        windowsRead(3, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        final List<BriefSampleRead> reads = events.stream()
                .filter(BriefSampleRead.class::isInstance)
                .map(BriefSampleRead.class::cast)
                .toList();
        assertThat(reads)
                .extracting(BriefSampleRead::location)
                .doesNotContain("")
                .isNotEmpty();
        assertThat(reads).extracting(BriefSampleRead::sent).allMatch(sent -> sent.startsWith("Passage 1: "));
        assertThat(requests(StepState.STARTED))
                .filteredOn(change -> change.label().startsWith("sample-"))
                .extracting(StepChanged::itemRef)
                .containsExactlyElementsOf(
                        reads.stream().map(BriefSampleRead::location).toList());
    }

    // The knowledge and synthesis steps are not places in the book, so they carry no item reference.
    @Test
    void suggestBrief_knowledgeAndSynthesisSteps_carryNoItemReference() {
        dialIs(QualityDial.FAST);
        knowledgeAnswers(KNOWN);
        windowsRead(3, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(requests(StepState.STARTED))
                .filteredOn(change -> !change.label().startsWith("sample-"))
                .extracting(StepChanged::itemRef)
                .containsOnlyNulls();
    }

    // The title is the file stem, so the model is not asked what it knows: four windows and the synthesis.
    @Test
    void suggestBrief_balancedTitleIsFileStem_plansFourSamplesSynthesisNoKnowledge() {
        bookTitled("---\ntitle: Book\n---\n\n", BriefBooks.story(12, 100));
        windowsRead(WINDOWS, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(plans()).containsExactly(new StepsPlanned(StepKind.REQUEST, 5, false));
        assertThat(startedLabels()).containsExactly("sample-1", "sample-2", "sample-3", "sample-4", "synthesis");
    }

    // Max reads five windows; a known title adds the knowledge request and the synthesis: seven.
    @Test
    void suggestBrief_maxKnownTitle_plansKnowledgeFiveSamplesSynthesis() {
        dialIs(QualityDial.MAX);
        knowledgeAnswers(KNOWN);
        windowsRead(5, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(plans()).containsExactly(new StepsPlanned(StepKind.REQUEST, 7, false));
        assertThat(startedLabels())
                .containsExactly("knowledge", "sample-1", "sample-2", "sample-3", "sample-4", "sample-5", "synthesis");
    }

    // A story too short for the dial's windows is read in fewer, and the plan counts the windows actually read.
    @Test
    void suggestBrief_shortStory_plansFewerSamples() {
        dialIs(QualityDial.MAX);
        bookTitled("---\ntitle: Book\n---\n\n", BriefBooks.story(1, 8));
        windowsRead(1, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(plans()).hasSize(1);
        assertThat(plans().getFirst().added())
                .isLessThan(7)
                .isEqualTo(startedLabels().size());
        assertThat(startedLabels()).startsWith("sample-1").endsWith("synthesis");
    }

    // IF a book is unknown and no window read genre, audience or voice, THEN there is nothing to synthesize and the
    // synthesis step ends with no call although it was planned.
    @Test
    void suggestBrief_nothingToSynthesize_endsSynthesisNoCall() {
        bookTitled("---\ntitle: Book\n---\n\n", BriefBooks.story(12, 100));
        windowsRead(WINDOWS, EMPTY_WINDOW);

        assertThat(suggest().isOk()).isTrue();

        assertThat(plans()).containsExactly(new StepsPlanned(StepKind.REQUEST, 5, false));
        assertThat(requests(StepState.ENDED))
                .filteredOn(change -> change.label().equals("synthesis"))
                .extracting(StepChanged::result)
                .containsExactly(StepResult.NO_CALL);
        assertThat(requestsOf(ua.bookloom.api.pipeline.CallKind.BRIEF_SYNTHESIS))
                .isEmpty();
    }

    // A window whose reply holds no object ends its step failed, so the dialog does not claim a reading.
    @Test
    void suggestBrief_windowReplyUnreadable_endsThatSampleFailed() {
        dialIs(QualityDial.FAST);
        knowledgeAnswers(KNOWN);
        windowsAnswer(window("crime", "formal"), "not an object", window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        assertThat(requests(StepState.ENDED))
                .filteredOn(change -> change.label().equals("sample-2"))
                .extracting(StepChanged::result)
                .containsExactly(StepResult.FAILED);
    }

    // Each window's answers go to the dialog with their quotes, so the person sees what each reading rested on.
    @Test
    void suggestBrief_sampleRead_announcesValueAndQuote() {
        dialIs(QualityDial.FAST);
        knowledgeAnswers(KNOWN);
        windowsRead(3, window("crime", "formal"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggest().isOk()).isTrue();

        final List<BriefSampleRead> reads = events.stream()
                .filter(BriefSampleRead.class::isInstance)
                .map(BriefSampleRead.class::cast)
                .toList();
        assertThat(reads).extracting(BriefSampleRead::sample).containsExactly(1, 2, 3);
        assertThat(reads).extracting(BriefSampleRead::samples).containsOnly(3);
        assertThat(reads.getFirst().answers().get(BriefField.GENRE)).isEqualTo(new SampleAnswer("crime", QUOTE));
    }
}
