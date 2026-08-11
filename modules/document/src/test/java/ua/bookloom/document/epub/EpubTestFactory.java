package ua.bookloom.document.epub;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Exposes construction of an {@link EpubReader}/{@link EpubWriter} pair sharing one in-memory registry to test
 * code outside this package — {@code ua.bookloom.document.DocumentServiceTest} cannot name the package-private
 * {@link OpenEpubRegistry} directly, so it goes through this factory instead. Test-only: no production code
 * depends on this class.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EpubTestFactory {

    /**
     * A reader and writer that share one registry, exactly as {@code DocumentService} requires so a document
     * opened by the reader can be found again by the writer.
     *
     * @param reader the reader
     * @param writer the writer, sharing the same registry as {@code reader}
     */
    public record ReaderAndWriter(EpubReader reader, EpubWriter writer) {}

    /**
     * Builds an {@link EpubReader}/{@link EpubWriter} pair backed by one fresh, shared registry.
     *
     * @return the pair
     */
    public static ReaderAndWriter newSharedPair() {
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        return new ReaderAndWriter(new EpubReader(registry), new EpubWriter(registry));
    }
}
