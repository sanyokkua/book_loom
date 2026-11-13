package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetManifestTest {

    // sha256sum of the two files below with LF line ends, and of "path<TAB>sha<LF>" lines sorted by path
    private static final String HASH_A = "1dca30c21fec3d70b07538b7b39ccd9d24a68a09c15d6c7a61f951c0e251daaa";
    private static final String HASH_B = "f957b19529906961933c5c30f8713c500a9bb5d9d0695c40d48c97a26a3594ec";
    private static final String ROOT = "be29a0d506a431efb9594a1a623be0fb80462d2ad029938fa64a6bbea80eeafa";
    private static final String MANIFEST = """
            {"id": "demo", "version": 1, "pair": "en-uk", "ownerData": false,
             "files": [{"path": "b.txt", "sha256": "x"}, {"path": "a.txt", "sha256": "x"}],
             "rootHash": "x"}
            """;

    @Test
    void of_inventedTwoFileFolder_hashesLfNormalisedFilesToTheWrittenRootHash(@TempDir final Path root)
            throws IOException {
        final Path folder = write(root, "alpha line\nbeta line\n");

        final DatasetManifest manifest = DatasetManifest.of(folder);

        assertThat(manifest.rootHash()).isEqualTo(ROOT);
        assertThat(manifest.files())
                .extracting(DatasetManifest.FileHash::sha256)
                .containsExactly(HASH_A, HASH_B);
        assertThat(manifest.id()).isEqualTo("demo");
        assertThat(manifest.pair()).isEqualTo("en-uk");
    }

    @Test
    void of_fileWithCrlfLineEnds_hashesLikeTheLfFile(@TempDir final Path root) throws IOException {
        final Path folder = write(root, "alpha line\r\nbeta line\r\n");

        assertThat(DatasetManifest.of(folder).rootHash()).isEqualTo(ROOT);
    }

    // An image or an EPUB is not UTF-8 text: its raw bytes are hashed, CR LF kept, as sha256sum prints them.
    @Test
    void of_binaryFile_hashesItsRawBytesAsSha256sumDoes(@TempDir final Path root) throws IOException {
        final Path folder = Files.createDirectories(root.resolve("eval/datasets/demo"));
        Files.writeString(
                folder.resolve("manifest.json"),
                "{\"id\":\"demo\",\"version\":1,\"pair\":\"en-uk\",\"ownerData\":false,"
                        + "\"files\":[{\"path\":\"c.jpg\",\"sha256\":\"x\"}],\"rootHash\":\"x\"}");
        Files.write(root.resolve("eval/c.jpg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x0D, 0x0A});

        final DatasetManifest manifest = DatasetManifest.of(folder);

        assertThat(manifest.files())
                .extracting(DatasetManifest.FileHash::sha256)
                .containsExactly("d571286743c4e2e4f7f97dd46dddd17b5e8f715d9d390cd786a89c9f9e23f548");
        assertThat(manifest.rootHash()).isEqualTo("29128e0a3d44aea479d37cf473385d298008e140a38e90335fd7dbe9c7e51291");
    }

    private static Path write(final Path root, final String a) throws IOException {
        final Path folder = Files.createDirectories(root.resolve("eval/datasets/demo"));
        Files.writeString(folder.resolve("manifest.json"), MANIFEST);
        Files.writeString(root.resolve("eval/a.txt"), a, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("eval/b.txt"), "second file\n", StandardCharsets.UTF_8);
        return folder;
    }
}
