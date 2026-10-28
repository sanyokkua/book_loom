package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DatasetManifestCheckTest {

    static Stream<Path> datasetFolders() throws IOException, URISyntaxException {
        final Path datasets = Path.of(
                DatasetManifestCheckTest.class.getResource("/eval/datasets").toURI());
        try (Stream<Path> dirs = Files.list(datasets)) {
            return dirs.filter(d -> Files.isRegularFile(d.resolve("manifest.json"))).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasetFolders")
    void manifest_everyDatasetFolder_hasFilesThatStillHashToTheirManifest(final Path folder) {
        final DatasetManifest stored = DatasetManifest.load(folder);

        final DatasetManifest recomputed = DatasetManifest.of(folder);

        assertThat(recomputed.files()).isEqualTo(stored.files());
        assertThat(recomputed.rootHash()).isEqualTo(stored.rootHash());
    }

    // The reference book's manifest names its twelve books and the four copies of its image, all invented data.
    @Test
    void manifest_articleGravity_listsTwelveBooksAndFourImagesOfInventedData() {
        final DatasetManifest manifest = DatasetManifest.loadBundled("article-gravity");

        assertThat(manifest.files()).hasSize(16);
        assertThat(manifest.files().stream().map(DatasetManifest.FileHash::path))
                .contains(
                        "datasets/article-gravity/en/gravity.md",
                        "datasets/article-gravity/fr/gravity.epub",
                        "datasets/article-gravity/ru/gravity.fb2",
                        "datasets/article-gravity/uk/Picture_of_earth.jpg");
        assertThat(manifest.pair()).isEqualTo("en-uk+en-ru+en-fr");
        assertThat(manifest.ownerData()).isFalse();
    }

    @Test
    void manifest_fileMissing_failsNamingThePath(@TempDir final Path root) throws IOException {
        final Path folder = Files.createDirectories(root.resolve("eval/datasets/demo"));
        Files.writeString(
                folder.resolve("manifest.json"),
                "{\"id\":\"demo\",\"version\":1,\"pair\":\"en-uk\",\"ownerData\":false,"
                        + "\"files\":[{\"path\":\"gone.txt\",\"sha256\":\"x\"}],\"rootHash\":\"x\"}");

        assertThatThrownBy(() -> DatasetManifest.of(folder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gone.txt");
    }
}
