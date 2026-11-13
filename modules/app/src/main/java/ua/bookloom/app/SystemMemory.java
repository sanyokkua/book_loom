package ua.bookloom.app;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.MemorySource;

/** The operating system's own figure for the computer's memory; reading it starts no process and opens no socket. */
@Slf4j
public final class SystemMemory implements MemorySource {

    @Override
    public long totalBytes() {
        final long bytes = ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os
                ? os.getTotalMemorySize()
                : 0;
        log.debug("total memory read: {} bytes", bytes);
        return bytes;
    }
}
