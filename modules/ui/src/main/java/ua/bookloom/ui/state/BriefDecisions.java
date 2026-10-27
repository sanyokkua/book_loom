package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.SampleAnswer;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * The rows of "What it decides", worked out for display only: while the samples are read, a tally of what they
 * answered, and once the answer has arrived, the pipeline's own decision with its evidence. The tally never decides
 * anything the pipeline decides; it counts only an answer that quotes the book, as the pipeline does.
 *
 * <p>The four rows are the mockup's: genre, register, who tells the story, and who reads it with the voice.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefDecisions {

    private static final List<BriefField> ROWS =
            List.of(BriefField.GENRE, BriefField.REGISTER, BriefField.NARRATOR, BriefField.AUDIENCE);

    /**
     * The tally of the samples read so far.
     *
     * @param reads what each sample answered, in reading order
     * @return the four rows; empty when no sample has been read
     */
    static List<DecisionRow> running(final List<BriefSampleRead> reads) {
        Objects.requireNonNull(reads, "reads");
        log.debug("decisions tallied over {} samples", reads.size());
        if (reads.isEmpty()) {
            return List.of();
        }
        return ROWS.stream().map(group -> tallied(group, reads)).toList();
    }

    /**
     * The rows of the decided answer.
     *
     * @param answer what the pipeline decided, with its evidence
     * @param reads what each sample answered, for the per-sample lines
     * @return the four rows, in the mockup's order
     */
    static List<DecisionRow> answered(final BriefSuggestion answer, final List<BriefSampleRead> reads) {
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(reads, "reads");
        log.debug("decisions of the answer over {} samples", reads.size());
        return ROWS.stream().map(group -> decided(group, answer, reads)).toList();
    }

    private static DecisionRow tallied(final BriefField group, final List<BriefSampleRead> reads) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, String> shown = new LinkedHashMap<>();
        for (final SampleLine line : linesOf(List.of(group), reads)) {
            if (line.verified()) {
                final String key = line.value().strip().toLowerCase(Locale.ROOT);
                counts.merge(key, 1, Integer::sum);
                shown.putIfAbsent(key, capitalized(line.value()));
            }
        }
        final Optional<Map.Entry<String, Integer>> best = counts.entrySet().stream()
                .reduce((first, second) -> second.getValue() > first.getValue() ? second : first);
        final int agreeing = best.map(Map.Entry::getValue).orElse(0);
        final String value = best.map(entry -> shown.get(entry.getKey())).orElse("");
        log.trace("tally of {}: '{}' {} of {}", group, value, agreeing, reads.size());
        final boolean split = !value.isEmpty() && agreeing * 2 <= reads.size();
        return new DecisionRow(group, value, agreeing, reads.size(), split, linesOf(fieldsOf(group), reads));
    }

    private static DecisionRow decided(
            final BriefField group, final BriefSuggestion answer, final List<BriefSampleRead> reads) {
        final String value = valueOf(group, answer);
        final Optional<FieldEvidence> proof = answer.evidenceOf(group);
        final int samples = proof.map(FieldEvidence::samples).orElse(reads.size());
        final int agreeing = proof.map(FieldEvidence::agreeing).orElse(0);
        final boolean uncertain = proof.map(found -> !found.isKept()).orElse(false);
        return new DecisionRow(group, value, agreeing, samples, uncertain, linesOf(fieldsOf(group), reads));
    }

    private static String valueOf(final BriefField group, final BriefSuggestion answer) {
        return switch (group) {
            case GENRE -> blankIfNull(answer.genre());
            case REGISTER -> registerWord(answer.register());
            case NARRATOR -> narratorWord(answer.narrator(), answer.narratorGender());
            case AUDIENCE -> readers(answer);
            case VOICE, NARRATOR_GENDER -> "";
        };
    }

    private static String registerWord(final Register register) {
        return switch (register) {
            case FORMAL_LITERARY -> "Formal";
            case NEUTRAL -> "Neutral";
            case CASUAL -> "Casual";
        };
    }

    private static String narratorWord(final NarratorPerson person, final Gender gender) {
        final String word =
                switch (person) {
                    case FIRST -> "First person";
                    case THIRD -> "Third person";
                    case UNSPECIFIED -> "";
                };
        final boolean known = gender == Gender.MALE || gender == Gender.FEMALE;
        return known && person == NarratorPerson.FIRST ? word + " · " + capitalized(gender.name()) : word;
    }

    private static String readers(final BriefSuggestion answer) {
        final String audience = blankIfNull(answer.audience());
        final String voice = blankIfNull(answer.voiceEra());
        if (audience.isEmpty() || voice.isEmpty()) {
            return audience + voice;
        }
        return audience + " · " + voice;
    }

    private static List<SampleLine> linesOf(final List<BriefField> fields, final List<BriefSampleRead> reads) {
        final List<SampleLine> lines = new ArrayList<>();
        for (final BriefField field : fields) {
            for (final BriefSampleRead read : reads) {
                final SampleAnswer answer = read.answers().get(field);
                if (answer != null) {
                    lines.add(lineOf(read, answer));
                }
            }
        }
        return lines;
    }

    private static List<BriefField> fieldsOf(final BriefField group) {
        return group == BriefField.AUDIENCE ? List.of(BriefField.AUDIENCE, BriefField.VOICE) : List.of(group);
    }

    private static SampleLine lineOf(final BriefSampleRead read, final SampleAnswer answer) {
        final boolean quoted = !answer.quote().isBlank() && !answer.value().isBlank();
        return new SampleLine(read.sample(), read.location(), answer.value(), answer.quote(), quoted);
    }

    private static String blankIfNull(final @Nullable String text) {
        return text == null ? "" : text.strip();
    }

    private static String capitalized(final String text) {
        final String word = text.strip();
        return word.isEmpty()
                ? word
                : word.substring(0, 1).toUpperCase(Locale.ROOT)
                        + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
