package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One source with a faithful candidate and, usually, a defective one: the unit of the real-run corpus (15e.2) for the
 * defect classes a production check can decide — quote balance, script purity, narrator gender, length — and the ones
 * only the reviewer can see. The same case is a draft (its source goes through the run's request factory), a deterministic
 * verdict (the production check must agree with the labels) and a reviewer pair.
 *
 * @param id the case id, unique across the corpus
 * @param kind the defect family, the grouping of the report: quotes, mixed-script, invented-word, russian-letters,
 *     narrator, short-line
 * @param check which production check decides the case; {@link Check#NONE} when none does
 * @param source the English source, masked as the document model masks it
 * @param good a faithful Ukrainian candidate the production check must leave alone
 * @param bad a defective candidate the check must refuse, or null for a case that holds only a clean candidate
 * @param deterministic whether a production check decides the labels; false for a case only the reviewer can judge
 * @param knownFailure whether production code is expected to get this case wrong today: it is reported on its own,
 *     outside every rate, and the test fails once the code gets it right so the flag is dropped with the fix
 * @param fixedBy the task that makes a known failure pass, or that adds the deterministic check, for the report
 * @param glossary the glossary the case holds
 * @param context what surrounds the segment: earlier pairs, summary, lexicon and the narrator
 * @param rationale why the labels are what they are
 */
record RunCase(
        String id,
        String kind,
        Check check,
        String source,
        String good,
        @Nullable String bad,
        @Nullable Boolean deterministic,
        boolean knownFailure,
        @Nullable String fixedBy,
        List<EvalTerm> glossary,
        @Nullable EvalContext context,
        String rationale) {

    /** The production check that decides a case. */
    enum Check {
        /** A blocking unbalanced-quote finding. */
        QUOTES,
        /** A blocking mixed-script finding. */
        SCRIPT,
        /** A soft target-alphabet finding. */
        ALPHABET,
        /** A narrator-gender finding. */
        GENDER,
        /** A failed length check. */
        LENGTH,
        /** No production check: only the reviewer can tell. */
        NONE
    }

    /** Rejects missing parts, copies the glossary and fills what a case file leaves out. */
    RunCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(good, "good");
        Objects.requireNonNull(rationale, "rationale");
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    /** Whether a production check decides this case's labels. */
    boolean isDeterministic() {
        return check != Check.NONE && (deterministic == null || deterministic);
    }

    /** The context the case states, or none. */
    EvalContext surroundings() {
        return context == null ? EvalContext.none() : context;
    }

    /** The draft case the run's request factory builds for the source. */
    EvalCase.Draft draft() {
        return new EvalCase.Draft(id, source, glossary, EvalCase.Expect.translate(), surroundings());
    }
}
