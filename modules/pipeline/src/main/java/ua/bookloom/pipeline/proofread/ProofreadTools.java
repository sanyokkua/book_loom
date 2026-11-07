package ua.bookloom.pipeline.proofread;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.pipeline.revision.CodeSweep;

/**
 * The collaborators the Proofread stage needs beyond the run's own stores.
 *
 * @param sweep the code sweep of renamed glossary targets
 * @param deferrals the deferral store, read for the deferrals whose fact arrived and written when one is settled
 */
public record ProofreadTools(CodeSweep sweep, DeferralRepository deferrals) {

    /** Rejects a missing collaborator. */
    @Inject
    public ProofreadTools {
        Objects.requireNonNull(sweep, "sweep");
        Objects.requireNonNull(deferrals, "deferrals");
    }
}
