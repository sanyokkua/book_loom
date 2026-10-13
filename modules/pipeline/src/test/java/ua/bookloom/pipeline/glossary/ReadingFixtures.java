package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Usage;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;

/**
 * Readings built by hand, as {@link TermReadingReplies} would leave them, for the tests of the code's verdict (15i.N5):
 * invented windows of the term {@code Brann} and of the word {@code Mark}.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReadingFixtures {

    static final String PROJECT = "p1";
    static final PronounGender.Evidence NO_TALLY = tally(0, 0);
    static final String MALE_TEXT = "Brann waved from the cart. He left before noon.";
    static final String FEMALE_TEXT = "Brann waved from the cart. She left before noon.";
    static final String WALL = "There was a mark on the wall behind the stove.";
    static final String IMPERATIVE = "Mark the spot, said the guard, and walked away.";

    /** One window as it was shown and as the reading kept its answer. */
    record Answer(String text, Usage usage, TermReading.Kind kind, String quote, Gender vote) {}

    static Answer name(final TermReading.Kind kind) {
        return new Answer("Brann crossed the yard with a lamp.", Usage.NAME, kind, "", Gender.UNKNOWN);
    }

    static Answer voting(final Gender gender) {
        return gender == Gender.MALE
                ? new Answer(MALE_TEXT, Usage.NAME, TermReading.Kind.PERSON, "He left before noon", Gender.MALE)
                : new Answer(FEMALE_TEXT, Usage.NAME, TermReading.Kind.PERSON, "She left before noon", Gender.FEMALE);
    }

    static Answer votingAs(final TermReading.Kind kind) {
        return new Answer(MALE_TEXT, Usage.NAME, kind, "He left before noon", Gender.MALE);
    }

    static Answer word(final String text, final String quote) {
        return new Answer(text, Usage.WORD, TermReading.Kind.OTHER, quote, Gender.UNKNOWN);
    }

    static Answer unsure() {
        return new Answer("Brann.", Usage.UNSURE, TermReading.Kind.OTHER, "", Gender.UNKNOWN);
    }

    static List<Answer> times(final int count, final Answer answer) {
        return Collections.nCopies(count, answer);
    }

    @SafeVarargs
    static List<Answer> answers(final List<Answer>... parts) {
        final List<Answer> all = new ArrayList<>();
        for (final List<Answer> part : parts) {
            all.addAll(part);
        }
        return all;
    }

    static ReadCall call(final String term, final List<Answer> answers) {
        final List<Window> windows = IntStream.range(0, answers.size())
                .mapToObj(index -> new Window(
                        new Mention(term, index, 0, term.length(), 0, true),
                        index,
                        index,
                        answers.get(index).text(),
                        0,
                        term.length()))
                .toList();
        final List<WindowReading> readings = IntStream.range(0, answers.size())
                .mapToObj(index -> {
                    final Answer answer = answers.get(index);
                    return new WindowReading(
                            index + 1, answer.quote(), answer.usage(), answer.kind(), answer.vote(), answer.vote());
                })
                .toList();
        return new ReadCall(windows, new Reading(term, readings, false, false));
    }

    static PronounGender.Evidence tally(final int she, final int he) {
        return new PronounGender.Evidence(Collections.nCopies(she, "she"), Collections.nCopies(he, "he"));
    }

    static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry(PROJECT + ":" + term, PROJECT, term, null, type, Gender.UNKNOWN, false);
    }

    static GlossaryEntry untyped() {
        return entry("Brann", TermType.OTHER);
    }

    static Facts facts(
            final GlossaryEntry entry,
            final List<Answer> answers,
            final PronounGender.Evidence tally,
            final double lowerShare,
            final QualityDial dial,
            @Nullable final String language) {
        return new Facts(
                entry,
                List.of(call(entry.term(), answers)),
                List.of(),
                new NameWindows.Evidence(answers.size(), List.of(), tally),
                lowerShare,
                dial,
                language);
    }
}
