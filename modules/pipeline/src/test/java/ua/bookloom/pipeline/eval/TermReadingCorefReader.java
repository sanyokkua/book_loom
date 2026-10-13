package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.glossary.TermReading;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The coreference probe's reader backed by the term reading (15i.N4): each window is one call with the target marked
 * where it first stands, and only the votes the reading keeps are claims, so the scorer counts what the code would
 * use. A failed call claims nothing, which the scorer counts as an abstention.
 *
 * @param reading the production term reading
 * @param frame the call frame, whose analysis language decides the pronoun check
 * @param calls the model-call seam
 */
record TermReadingCorefReader(TermReading reading, CallFrame frame, ModelCalls calls) implements CorefReader {

    private static final PronounGender.Evidence NO_TALLY = new PronounGender.Evidence(List.of(), List.of());

    @Override
    public List<CorefClaim> read(final String term, final String windowText) {
        final int start = Math.max(0, windowText.indexOf(term));
        final int end = windowText.contains(term) ? start + term.length() : start;
        final Window window = new Window(new Mention(term, 0, start, end, 0, true), 0, 0, windowText, start, end);
        final Result<Reading> read =
                reading.read(new TermReading.Subject(term, 1, NO_TALLY, List.of()), List.of(window), frame, calls);
        if (read.isErr()) {
            return List.of();
        }
        return Objects.requireNonNull(read.data()).windows().stream()
                .filter(answer -> answer.vote() != Gender.UNKNOWN)
                .map(answer -> new CorefClaim(answer.quote(), true))
                .toList();
    }
}
