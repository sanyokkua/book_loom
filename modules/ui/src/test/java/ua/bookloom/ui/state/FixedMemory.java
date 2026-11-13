package ua.bookloom.ui.state;

import ua.bookloom.ui.MemorySource;

/** A computer with a stated amount of memory, so a recommendation never depends on the machine the test runs on. */
public final class FixedMemory implements MemorySource {

    private static final long BYTES_PER_GB = 1L << 30;

    private long bytes;

    public FixedMemory(final long gigabytes) {
        this.bytes = gigabytes * BYTES_PER_GB;
    }

    /** Changes the amount before a screen is built that reads it. */
    public void gigabytes(final long gigabytes) {
        this.bytes = gigabytes * BYTES_PER_GB;
    }

    @Override
    public long totalBytes() {
        return bytes;
    }
}
