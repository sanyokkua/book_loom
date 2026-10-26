package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.SqlitePersistenceModule;
import ua.bookloom.pipeline.UuidIdSource;

/**
 * Importing a large plain-text book into the SQLite store fits the test task's 512 MB heap. Before the book index
 * stopped keeping every word of every segment as an object, a 20 MB book ran out of memory here (the index alone held
 * about 490 MB of it; now about 160 MB).
 */
// `slow`: a 20 MB book through the whole import.
@Slf4j
@Tag("slow")
class LargeTextImportMemoryTest {

    private static final long BOOK_BYTES = 20L * 1024 * 1024;
    private static final long MEGABYTE = 1024L * 1024;
    // Gradle's default test heap; a larger one would let the import pass without proving the bound.
    private static final long HEAP_BOUND_MEGABYTES = 512;
    private static final String PARAGRAPH = "The old lighthouse keeper climbed the narrow stairs every evening, counting"
            + " the steps aloud as his father had done, and when he reached the lamp room he wiped the glass,"
            + " trimmed the wick and watched the grey sea for ships that no longer came. Number ";

    @TempDir
    private Path dir;

    @Test
    void create_twentyMegabyteText_storesItWithABoundedPeakHeap() throws Exception {
        final Path book = largeBook(dir.resolve("Lighthouse.txt"));
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new SqlitePersistenceModule(dir.resolve("bookloom.db"), Clock.systemUTC()),
                binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(new UuidIdSource());
                });
        final ProjectServiceImpl service = injector.getInstance(ProjectServiceImpl.class);
        resetPeaks();

        final Result<ImportedBook> imported = service.create(book);

        final long maxMegabytes = Runtime.getRuntime().maxMemory() / MEGABYTE;
        log.info("20 MB text import: peak heap {} MB of {} MB", peakHeapMegabytes(), maxMegabytes);
        assertThat(maxMegabytes).as("the bound this test proves").isLessThanOrEqualTo(HEAP_BOUND_MEGABYTES);
        assertThat(imported.isOk()).as("%s", imported.error()).isTrue();
    }

    private static Path largeBook(final Path file) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            long written = 0;
            for (int i = 0; written < BOOK_BYTES; i++) {
                final String paragraph = PARAGRAPH + i + ".\n\n";
                out.write(paragraph);
                written += paragraph.length();
            }
        }
        return file;
    }

    private static void resetPeaks() {
        System.gc();
        heapPools().forEach(MemoryPoolMXBean::resetPeakUsage);
    }

    private static long peakHeapMegabytes() {
        return heapPools().stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum() / MEGABYTE;
    }

    private static List<MemoryPoolMXBean> heapPools() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .toList();
    }
}
