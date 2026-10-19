package ua.bookloom.pipeline.run;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.project.RollingSummary;

/**
 * The labels design D5 gives a glossary and a summary update, in one place, so the preparation stage and a unit's end
 * announce names the same way.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemoryEvents {

    /**
     * The announcement of names a scan added to the glossary.
     *
     * @param added how many entries the glossary gained; at least 1, since nothing is announced for none
     * @return the event labelled {@code +n}
     */
    public static MemoryUpdated namesAdded(final int added) {
        log.debug("Sending MemoryUpdated kind={} label=+{}", MemoryKind.GLOSSARY, added);
        return new MemoryUpdated(MemoryKind.GLOSSARY, "+" + added);
    }

    /**
     * The announcement of a refreshed rolling summary.
     *
     * @param summary the non-null new version
     * @return the event labelled with the version number
     */
    static MemoryUpdated summaryRefreshed(final RollingSummary summary) {
        log.debug("Sending MemoryUpdated kind={} label={}", MemoryKind.SUMMARY, summary.version());
        return new MemoryUpdated(MemoryKind.SUMMARY, String.valueOf(summary.version()));
    }
}
