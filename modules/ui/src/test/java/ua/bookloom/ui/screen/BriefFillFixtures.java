package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SampleAnswer;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/** The events a brief suggestion announces to its dialog: the steps of a Balanced run and what each sample answered. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefFillFixtures {

    static final BriefSuggestion ANSWER = new BriefSuggestion(
            "crime",
            Register.CASUAL,
            "terse and wry",
            "adult readers",
            NarratorPerson.THIRD,
            Gender.UNKNOWN,
            Map.of(
                    BriefField.GENRE,
                    new FieldEvidence(FieldEvidence.Basis.PASSAGES, List.of(), 4, 4),
                    BriefField.REGISTER,
                    new FieldEvidence(
                            FieldEvidence.Basis.PASSAGES, List.of("He left before the lamps were lit."), 3, 4)));

    static final String QUOTE = "He left before the lamps were lit.";
    static final List<String> LOCATIONS = List.of("ch1 · p02–p14", "ch1 · p15–p27", "ch2 · p03–p12", "ch3 · p01–p20");

    private static StepChanged started(final String label, final int index, final @Nullable String place) {
        return new StepChanged(StepKind.REQUEST, label, index, StepState.STARTED, place, null);
    }

    private static StepChanged ended(final String label, final int index, final @Nullable String place) {
        return new StepChanged(StepKind.REQUEST, label, index, StepState.ENDED, place, StepResult.DONE);
    }

    private static BriefSampleRead read(final int sample, final String register, final String quote) {
        return new BriefSampleRead(
                sample,
                4,
                Map.of(BriefField.REGISTER, new SampleAnswer(register, quote)),
                LOCATIONS.get(sample - 1),
                "Passage 1: " + QUOTE);
    }

    /**
     * Every event of a Balanced run, as the pipeline announces them.
     *
     * @param knowledgeAsked whether the book's title is known, so the model is asked what it knows
     * @param registers what each of the four samples answered for the register
     * @param quotes what each sample quoted for it, empty for none
     * @return the events in order
     */
    static List<JobEvent> balancedRun(
            final boolean knowledgeAsked, final List<String> registers, final List<String> quotes) {
        final List<JobEvent> events = new ArrayList<>();
        final int offset = knowledgeAsked ? 1 : 0;
        events.add(new StepsPlanned(StepKind.REQUEST, offset + 4 + 1, false));
        if (knowledgeAsked) {
            events.add(started("knowledge", 0, null));
            events.add(ended("knowledge", 0, null));
        }
        for (int sample = 1; sample <= 4; sample++) {
            final int index = offset + sample - 1;
            final String label = "sample-" + sample;
            events.add(started(label, index, LOCATIONS.get(sample - 1)));
            events.add(read(sample, registers.get(sample - 1), quotes.get(sample - 1)));
            events.add(ended(label, index, LOCATIONS.get(sample - 1)));
        }
        events.add(started("synthesis", offset + 4, null));
        events.add(ended("synthesis", offset + 4, null));
        return events;
    }

    /** The run in which three of four samples answer casual with a quote that is in the book. */
    static List<JobEvent> threeOfFourCasual(final boolean knowledgeAsked) {
        return balancedRun(
                knowledgeAsked, List.of("casual", "casual", "neutral", "casual"), List.of(QUOTE, QUOTE, QUOTE, QUOTE));
    }

    /** The steps of a run whose samples have started but not ended, with no answer yet. */
    static List<JobEvent> firstSampleStarted() {
        return List.of(
                new StepsPlanned(StepKind.REQUEST, 6, false),
                started("knowledge", 0, null),
                ended("knowledge", 0, null),
                started("sample-1", 1, LOCATIONS.getFirst()));
    }
}
