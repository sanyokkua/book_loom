package ua.bookloom.ui.control;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.RaiserWords;

/**
 * A proofread verdict in words: the outcome, then why — for a kept rewrite what the window was marked for, for a refused
 * one the check the rewrite made worse and the words that broke it, for a proposal why it waits for the person. The
 * stage stores its reasons as check names and English notes the model is shown; they are read here, once, so the live
 * card and the review desk word a verdict alike. Pure; logs nothing, because the cells call it on every redraw.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProofreadWords {

    private static final String WORDS = ": ";
    private static final String GENDER_MARK = "gender ending against";
    private static final String GENDER_KNOWN = "gender now known: ";
    private static final String NAME_GENDER = " — ";
    private static final String STILL_BLOCKED = "still blocked";
    private static final List<String> VETO_PREFIXES = List.of("new-block:", "count:");
    private static final List<String> MARK_PREFIXES = List.of("flagged: ", "proofread mark: ", "audit ");

    /**
     * A verdict as one line, such as "kept: gender ending fixed" or "rejected: added an untranslated word «что»".
     *
     * @param messages the non-null catalogue
     * @param outcome the non-null outcome
     * @param reason the non-null stored reason; empty when the stage gave none
     * @param marks the non-null reasons the segment was marked for; empty when it rode along unmarked
     * @return the outcome alone when nothing more is known, else the outcome and the reason
     */
    public static String verdict(
            final Messages messages, final ProofreadOutcome outcome, final String reason, final List<String> marks) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(marks, "marks");
        final String word = messages.code(MessageKey.PROOF_VERDICT, outcome.name());
        final String why =
                switch (outcome) {
                    case KEPT -> keptWhy(messages, reason, marks);
                    case REJECTED -> reason.isBlank() ? "" : rejected(messages, reason);
                    case PROPOSED -> reason.isBlank() ? "" : proposed(messages, reason);
                    case UNCHANGED -> "";
                };
        return why.isEmpty() ? word : messages.get(MessageKey.PROOF_VERDICT_WHY, word, why);
    }

    // A window's marks say why it was read; a stored verdict keeps the first of them as its reason.
    private static String keptWhy(final Messages messages, final String reason, final List<String> marks) {
        if (!marks.isEmpty()) {
            return kept(messages, marks.getFirst());
        }
        return reason.isBlank() ? "" : kept(messages, reason);
    }

    private static String kept(final Messages messages, final String mark) {
        if (mark.startsWith(GENDER_MARK)) {
            return messages.get(MessageKey.PROOF_KEPT_GENDER);
        }
        if (mark.startsWith(GENDER_KNOWN) && mark.contains(NAME_GENDER)) {
            final String fact = mark.substring(GENDER_KNOWN.length());
            final int split = fact.lastIndexOf(NAME_GENDER);
            return messages.get(
                    MessageKey.PROOF_KEPT_GENDER_KNOWN,
                    fact.substring(0, split),
                    fact.substring(split + NAME_GENDER.length()));
        }
        return messages.get(MessageKey.PROOF_KEPT_CHECK, RaiserWords.raiser(messages, checkOfMark(mark)));
    }

    // "flagged: gender, number", "proofread mark: number", "audit negation: note" or "number: note" name the check
    // first.
    private static String checkOfMark(final String mark) {
        String rest = mark;
        for (final String prefix : MARK_PREFIXES) {
            if (rest.startsWith(prefix)) {
                rest = rest.substring(prefix.length());
            }
        }
        final int end = firstOf(rest, ':', ',');
        return (end < 0 ? rest : rest.substring(0, end)).strip();
    }

    private static int firstOf(final String text, final char one, final char other) {
        final int first = text.indexOf(one);
        final int second = text.indexOf(other);
        if (first < 0) {
            return second;
        }
        return second < 0 ? first : Math.min(first, second);
    }

    private static String rejected(final Messages messages, final String reason) {
        final int split = reason.indexOf(WORDS);
        String veto = split < 0 ? reason : reason.substring(0, split);
        for (final String prefix : VETO_PREFIXES) {
            if (veto.startsWith(prefix)) {
                veto = veto.substring(prefix.length());
            }
        }
        final String said = messages.code(
                MessageKey.PROOF_REJECTED, veto, RaiserWords.raiser(messages, veto.toLowerCase(Locale.ROOT)));
        return split < 0 ? said : messages.get(MessageKey.PROOF_REJECTED_WORDS, said, reason.substring(split + 2));
    }

    private static String proposed(final Messages messages, final String reason) {
        if (reason.startsWith(STILL_BLOCKED)) {
            final int split = reason.indexOf(WORDS);
            final String checks = split < 0
                    ? messages.get(MessageKey.PROOF_A_CHECK)
                    : String.join(
                            ", ",
                            Arrays.stream(reason.substring(split + 2).split(",\\s*"))
                                    .map(check -> RaiserWords.raiser(messages, check))
                                    .toList());
            return messages.get(MessageKey.PROOF_PROPOSED_BLOCKED, checks);
        }
        return messages.get(MessageKey.PROOF_PROPOSED_EDIT);
    }
}
