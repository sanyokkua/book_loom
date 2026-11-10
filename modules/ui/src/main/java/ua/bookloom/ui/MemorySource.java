package ua.bookloom.ui;

import com.google.inject.ImplementedBy;

/**
 * How much memory this computer has, for the model advice only. The composition root binds the operating system's
 * figure; without it the amount is unknown and no advice is shown.
 */
@ImplementedBy(MemorySource.Unknown.class)
public interface MemorySource {

    /** The computer's total memory in bytes, or zero when it cannot be read. */
    long totalBytes();

    /** Knows nothing: the binding for a graph that does not read the computer. */
    final class Unknown implements MemorySource {

        @Override
        public long totalBytes() {
            return 0;
        }
    }
}
