package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PastTenseReader;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.setup.BriefDecision.Decided;

/**
 * The first-person narrator's gender, taken in order (book-brief "Keep the narrator's most probable gender as likely
 * and verify it only by evidence the code checks"): the source's own past forms, then the windows' verified votes, then
 * the synthesis's most probable gender. The model always gives its most probable gender;
 * it stands as the book's, with its quote, only when a window read holds the quote, the narrator does not say it to
 * someone else, and the quote's name or words of address prove a gender ({@link NarratorGenderEvidence}), which is then
 * the gender whatever the model said. Otherwise the model's gender is kept as likely, with no quote, so nobody takes it
 * for a fact and no check holds a text to it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NarratorGenderOrder {

    /**
     * The narrator's gender in order.
     *
     * @param book the book, whose source narration is counted first
     * @param answered the gender the windows' votes decided, with its evidence; kept when it is known
     * @param synthesis the synthesis reply, or null when the call failed, was skipped or could not be read
     * @param windows the windows read, where the quote must stand
     * @param genders the check of a narrator quote for this book
     * @return the source's verified count; else {@code answered} when it holds a gender or the synthesis gave none;
     *     else the proven gender with its quote, or the model's gender as likely
     */
    static Decided<Gender> of(
            final SetupBook book,
            final Decided<Gender> answered,
            final @Nullable JsonNode synthesis,
            final List<BookSample> windows,
            final NarratorGenderEvidence genders) {
        return fromSource(book).orElseGet(() -> fromSynthesis(answered, synthesis, windows, genders));
    }

    private static Decided<Gender> fromSynthesis(
            final Decided<Gender> answered,
            final @Nullable JsonNode synthesis,
            final List<BookSample> windows,
            final NarratorGenderEvidence genders) {
        final JsonNode node = synthesis == null ? null : synthesis.get("narrator");
        final Gender said =
                node == null ? Gender.UNKNOWN : genderOf(node.path("gender").asText(""));
        if (node == null || answered.value() != Gender.UNKNOWN || said == Gender.UNKNOWN) {
            log.debug("Synthesis narrator not used: windows decided {}, synthesis said {}", answered.value(), said);
            return answered;
        }
        final String quote = node.path("quote").asText("").strip();
        final String name = node.path("name").asText("").strip();
        final int samples = answered.evidence().samples();
        final Optional<Gender> proven = quote.isEmpty() ? Optional.empty() : proven(quote, name, windows, genders);
        log.trace("Synthesis narrator quote '{}' name '{}'", quote, name);
        if (proven.isPresent()) {
            log.info("Narrator gender {} proven by the synthesis's quote (model said {})", proven.get(), said);
            return new Decided<>(proven.get(), new FieldEvidence(Basis.PASSAGES, List.of(quote), 1, samples));
        }
        log.info("Narrator gender {} kept as likely: the synthesis's quote proves nothing the code can check", said);
        return new Decided<>(said, new FieldEvidence(Basis.LIKELY, List.of(), 0, samples));
    }

    // The narrator's own past forms in the source's narration: a verified count is the book's evidence and replaces
    // the model's answer; a language with no gendered past forms counts nothing, and the later steps decide.
    private static Optional<Decided<Gender>> fromSource(final SetupBook book) {
        final String language = book.languageOf();
        final PastTenseReader reader = PastTenseReader.of(language);
        if (reader == null) {
            log.debug("Narrator not read from the source language={}: no gendered past forms", language);
            return Optional.empty();
        }
        final PastTenseReader.FirstPersonCount count =
                reader.firstPerson(NarratorDetector.paragraphsOf(book.document()));
        log.info(
                "Narrator from source language={} forms={} agreeing={} verified={}",
                language,
                count.forms(),
                count.agreeing(),
                count.verified());
        if (!count.verified()) {
            return Optional.empty();
        }
        return Optional.of(new Decided<>(
                count.gender(), new FieldEvidence(Basis.MEASURED, count.quotes(), count.agreeing(), count.forms())));
    }

    // The gender the quote proves in the first window that holds it, unless the narrator says it to someone else.
    private static Optional<Gender> proven(
            final String quote,
            final String name,
            final List<BookSample> windows,
            final NarratorGenderEvidence genders) {
        for (final BookSample window : windows) {
            if (window.holds(quote)) {
                final boolean toAnother = genders.isSaidToAnother(window, quote, name);
                log.debug("Synthesis narrator quote found in a window; said to another: {}", toAnother);
                return toAnother ? Optional.empty() : genders.genderOf(quote, name);
            }
        }
        log.debug("Synthesis narrator quote is in no window read");
        return Optional.empty();
    }

    private static Gender genderOf(final String word) {
        return switch (word.strip()) {
            case "male" -> Gender.MALE;
            case "female" -> Gender.FEMALE;
            default -> Gender.UNKNOWN;
        };
    }
}
