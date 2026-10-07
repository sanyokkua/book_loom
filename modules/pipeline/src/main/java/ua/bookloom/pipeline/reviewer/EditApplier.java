package ua.bookloom.pipeline.reviewer;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies a segment's edits one after another, each verified against the text the earlier ones left, so two edits that
 * overlap or depend on each other can never apply half. Fluency and style edits are set aside as notes first.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EditApplier {

    private final EditVerifier verifier;

    /**
     * Applies the verified edits of one segment.
     *
     * @param candidate the whole candidate in masked form; never null
     * @param edits the reviewer's edits in the order it wrote them; never null
     * @param checker runs the deterministic checks on a whole candidate; never null
     * @param baseline the checks that already block the candidate; never null
     * @return the edited text and what became of every edit
     */
    public EditOutcome apply(
            final String candidate,
            final List<ReviewEdit> edits,
            final CandidateChecker checker,
            final Set<String> baseline) {
        return apply(candidate, edits, checker, baseline, List.of());
    }

    /**
     * Applies the verified edits of one segment, knowing the glossary renderings of the chunk.
     *
     * @param renderings the glossary renderings (targets) a terminology edit may use; never null
     * @return the edited text and what became of every edit
     */
    public EditOutcome apply(
            final String candidate,
            final List<ReviewEdit> edits,
            final CandidateChecker checker,
            final Set<String> baseline,
            final List<String> renderings) {
        return apply(candidate, edits, checker, baseline, renderings, Set.of());
    }

    /**
     * Applies the verified edits of one segment, also knowing the target language's function words.
     *
     * @param functionWords the words an agreement or gender edit may neither add nor drop; never null, empty when the
     *     language lists none
     * @return the edited text and what became of every edit
     */
    public EditOutcome apply(
            final String candidate,
            final List<ReviewEdit> edits,
            final CandidateChecker checker,
            final Set<String> baseline,
            final List<String> renderings,
            final Set<String> functionWords) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(edits, "edits");
        String text = candidate;
        final List<ReviewEdit> applied = new ArrayList<>();
        final List<EditOutcome.FailedEdit> failed = new ArrayList<>();
        final List<ReviewEdit> notes = new ArrayList<>();
        int ignored = 0;
        for (final ReviewEdit edit : edits.stream().distinct().toList()) {
            if (edit.criterion().isNote()) {
                notes.add(edit);
                continue;
            }
            switch (verifier.verify(text, edit, checker, baseline, renderings, functionWords)) {
                case Verification.Verified verified -> {
                    text = verified.edited();
                    applied.add(edit);
                }
                case Verification.Failed refused -> failed.add(new EditOutcome.FailedEdit(edit, refused.reason()));
                case Verification.Ignored skipped -> ignored++;
            }
        }
        log.debug(
                "Applied edits applied={} failed={} ignored={} notes={}",
                applied.size(),
                failed.size(),
                ignored,
                notes.size());
        return new EditOutcome(text, applied, failed, ignored, notes);
    }
}
