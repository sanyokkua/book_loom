package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * The bounds on an untrusted container, and the legacy entry-name retry — both stated over <em>every</em> zip
 * container rather than over EPUB, because a cap that guards one of two entry points guards neither: an attacker
 * picks the extension.
 */
class ZipEntryReaderLimitsTest {

    private static final int OVER_THE_PRODUCTION_ENTRY_LIMIT = 10_001;

    /** Eight megabytes of zeros: a few kilobytes stored, ~1000:1 inflated — well past the production ratio. */
    private static final int BOMB_INFLATED_BYTES = 8 * 1024 * 1024;

    private static final ZipEntryReader.Limits TINY_LIMITS = new ZipEntryReader.Limits(3, 64, 10);

    // WHEN an archive expands beyond the configured total-size limit, THEN it is refused
    // rather than exhausting memory, and no content is returned.
    @Test
    void readAll_expansionBeyondTheTotalSizeLimit_isRefused() {
        final byte[] archive = zipOf(entry("big.bin", "x".repeat(1024)));

        assertThatThrownBy(() -> ZipEntryReader.readAll(archive, TINY_LIMITS))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("limit");
    }

    // WHEN an archive declares more entries than the configured limit, THEN it is refused.
    @Test
    void readAll_entryCountBeyondTheLimit_isRefused() {
        final byte[] archive = zipOf(entry("a", "1"), entry("b", "2"), entry("c", "3"), entry("d", "4"));

        assertThatThrownBy(() -> ZipEntryReader.readAll(archive, TINY_LIMITS))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("entries");
    }

    /**
     * Proves the production limits are actually wired, not merely defined — a limit only a test-supplied value
     * ever reaches would leave the real entry point unguarded.
     */
    // a decompression bomb arriving through the ordinary entry point is refused rather
    // than exhausting memory.
    @Test
    void readAll_decompressionBomb_isRefusedUnderTheProductionLimits() {
        final byte[] bomb = zipOf(entry("bomb.bin", new byte[BOMB_INFLATED_BYTES]));

        assertThatThrownBy(() -> ZipEntryReader.readAll(bomb))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("ratio");
    }

    // an implausible entry count is refused through the ordinary entry point too.
    @Test
    void readAll_implausibleEntryCount_isRefusedUnderTheProductionLimits() {
        assertThatThrownBy(() -> ZipEntryReader.readAll(manyEmptyEntries()))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("entries");
    }

    // IF a container's entry names cannot be decoded as UTF-8, THEN the container is
    // re-read in the zip format's historical default code page rather than reported as corrupt.
    @Test
    void readAll_legacyEncodedEntryName_isReadRatherThanRefused() {
        // Written in CP866, the code page real Russian archivers use for Cyrillic entry names. IBM437 itself
        // cannot encode Cyrillic at all — it is the code page the reader decodes *with*, chosen because it maps
        // every byte and so never throws, which is exactly what makes it a usable last resort.
        final byte[] archive = zipWithEntryNameIn(Charset.forName("IBM866"), "книга.fb2");

        final List<RawEntry> entries = ZipEntryReader.readAll(archive);

        assertThat(entries).hasSize(1);
        assertThat(new String(entries.get(0).content(), StandardCharsets.UTF_8)).isEqualTo("body");
    }

    @Test
    void readAll_ordinaryArchive_readsEveryEntryInStreamOrder() {
        final byte[] archive = zipOf(entry("a.txt", "A"), entry("b.txt", "B"), entry("c.txt", "C"));

        assertThat(ZipEntryReader.readAll(archive))
                .extracting(RawEntry::name)
                .containsExactly("a.txt", "b.txt", "c.txt");
    }

    @Test
    void readAll_emptyArchive_isRefused() {
        assertThatThrownBy(() -> ZipEntryReader.readAll(zipOf())).isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void readAll_notAZipAtAll_isRefused() {
        assertThatThrownBy(() -> ZipEntryReader.readAll("plain text, not a container".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CorruptContainerException.class);
    }

    @SuppressWarnings("ArrayRecordComponent") // deliberate: a test-only carrier for raw entry bytes; nothing
    // compares two of these, so the equals()/hashCode() concern the check exists for does not arise.
    private record Entry(String name, byte[] content) {}

    private static Entry entry(String name, String content) {
        return new Entry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static Entry entry(String name, byte[] content) {
        return new Entry(name, content);
    }

    private static byte[] zipOf(Entry... entries) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (final Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Built with a loop in a helper rather than in a test body — {@code .claude/rules/testing.md} bans control
     * flow in a test method, not in the fixture construction a test needs.
     */
    private static byte[] manyEmptyEntries() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < OVER_THE_PRODUCTION_ENTRY_LIMIT; i++) {
                zip.putNextEntry(new ZipEntry("e" + i));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Writes an archive whose entry name is encoded in {@code charset} without the UTF-8 flag — the shape older
     * tools produce and the default decoder throws on.
     */
    private static byte[] zipWithEntryNameIn(Charset charset, String name) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, charset)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write("body".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
