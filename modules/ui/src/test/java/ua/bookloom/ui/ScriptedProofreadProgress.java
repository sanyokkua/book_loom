package ua.bookloom.ui;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProofreadProgress;
import ua.bookloom.api.pipeline.ProofreadTally;

/** A hand-written {@link ProofreadProgress} that answers with one scripted tally, none by default. */
public final class ScriptedProofreadProgress implements ProofreadProgress {

    private volatile @Nullable ProofreadTally tally;

    /** Makes every project answer with {@code scripted}; {@code null} answers no window left. */
    public void tallyIs(final @Nullable ProofreadTally scripted) {
        tally = scripted;
    }

    @Override
    public Result<Optional<ProofreadTally>> tally(final String projectId, final int batchSize) {
        return Result.ok(Optional.ofNullable(tally));
    }
}
