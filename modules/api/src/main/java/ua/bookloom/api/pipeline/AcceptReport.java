package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * What one Accept all did: the suggested targets that passed the target language's checks and were accepted, and the
 * ones left suggested with the reasons they failed (design D4).
 *
 * @param accepted the entries as accepted, pinned or locked; never null, empty when none passed
 * @param rejected the entries left suggested, each with its reasons; never null, empty when none failed
 */
public record AcceptReport(List<GlossaryEntry> accepted, List<Rejected> rejected) {

    /** Copies the lists. */
    public AcceptReport {
        accepted = List.copyOf(Objects.requireNonNull(accepted, "accepted"));
        rejected = List.copyOf(Objects.requireNonNull(rejected, "rejected"));
    }

    /**
     * A suggested target Accept all left out.
     *
     * @param entry the entry as it stays, still suggested
     * @param reasons why, in words the person reads ({@code not the dictionary form}); never null or empty
     */
    public record Rejected(GlossaryEntry entry, List<String> reasons) {

        /** Validates the entry and copies the reasons. */
        public Rejected {
            Objects.requireNonNull(entry, "entry");
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        }
    }

    /**
     * How many suggestions were accepted.
     *
     * @return the size of {@link #accepted()}
     */
    public int acceptedCount() {
        return accepted.size();
    }

    /**
     * How many suggestions were left out.
     *
     * @return the size of {@link #rejected()}
     */
    public int rejectedCount() {
        return rejected.size();
    }
}
