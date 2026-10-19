package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A synthetic model reply to a one-segment draft and what the production reader and judge must make of it: the reply
 * is read by the draft reply parser, then judged by {@link ReplyJudge}. Synthetic text only, never copied from a book.
 *
 * @param id the case id, unique across the corpus
 * @param kind the defect family: control-chars, closer-residue, mixed-script-tail, gender-name, slang, numbers
 * @param sourceLanguage the source language tag, or null for English; the target is Ukrainian
 * @param source the masked source segment
 * @param reply the reply text exactly as a model would send it, JSON escapes included
 * @param expect what production does with the reply
 * @param knownFailure whether production gets it wrong today: it takes a defective reply, and the test fails once it
 *     refuses it so the flag is dropped with the fix
 * @param fixedBy the plan task that makes a known failure right, or that changes a constant expectation
 * @param glossary the glossary the case holds
 * @param rationale why the case exists
 */
record ReplyCase(
        String id,
        String kind,
        @Nullable String sourceLanguage,
        String source,
        String reply,
        Expect expect,
        boolean knownFailure,
        @Nullable String fixedBy,
        List<EvalTerm> glossary,
        String rationale) {

    /** What production does with a reply. */
    enum Expect {
        /** The reply is read and accepted as drafted. */
        ACCEPTED,
        /** The reply is refused: unreadable, or read and blocked by a gate or a text check. */
        REFUSED,
        /** The reply carries control characters where « » belong: {@link ReplyExpectations#CONTROL_CHARS}. */
        CONTROL_CHARS,
        /** The reply has text after its JSON object: {@link ReplyExpectations#TRAILING_CLOSERS}. */
        TRAILING_CLOSERS
    }

    /** Rejects missing parts and copies the glossary. */
    ReplyCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(expect, "expect");
        Objects.requireNonNull(rationale, "rationale");
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    String language() {
        return sourceLanguage == null ? "en" : sourceLanguage;
    }

    /** What production is expected to do, with the constants resolved. */
    Expect resolved() {
        return switch (expect) {
            case CONTROL_CHARS -> ReplyExpectations.CONTROL_CHARS;
            case TRAILING_CLOSERS -> ReplyExpectations.TRAILING_CLOSERS;
            case ACCEPTED, REFUSED -> expect;
        };
    }
}
