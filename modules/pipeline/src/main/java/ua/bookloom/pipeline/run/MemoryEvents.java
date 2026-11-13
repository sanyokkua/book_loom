package ua.bookloom.pipeline.run;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;

/**
 * The label design D5 gives a glossary update, in one place, so the preparation stage and a unit's end announce names
 * the same way.
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
}
