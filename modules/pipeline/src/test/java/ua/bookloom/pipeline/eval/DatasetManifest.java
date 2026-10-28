package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * A dataset is trusted only while its files hash to what its manifest says. File paths are relative to the {@code eval}
 * resource folder (the grandparent of the dataset folder), so a manifest may list files that sit elsewhere. Text files
 * ({@code .json .jsonl .md .txt .fb2 .csv}) hash with LF line ends; every other file hashes as its raw bytes.
 *
 * @param rootHash SHA-256 of the lines {@code path<TAB>sha256<LF>} sorted by path
 */
@Slf4j
record DatasetManifest(String id, int version, String pair, boolean ownerData, List<FileHash> files, String rootHash) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MANIFEST_FILE = "manifest.json";
    private static final List<String> TEXT_SUFFIXES = List.of(".json", ".jsonl", ".md", ".txt", ".fb2", ".csv");

    record FileHash(String path, String sha256) {}

    DatasetManifest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(rootHash, "rootHash");
        files = List.copyOf(files);
    }

    /** The manifest as written in {@code folder/manifest.json}, nothing recomputed. */
    static DatasetManifest load(final Path folder) {
        log.debug("loading dataset manifest folder={}", folder);
        final JsonNode json = readJson(folder.resolve(MANIFEST_FILE));
        final List<FileHash> files = json.get("files")
                .valueStream()
                .map(f -> new FileHash(f.get("path").asText(), f.get("sha256").asText()))
                .toList();
        final DatasetManifest manifest = new DatasetManifest(
                json.get("id").asText(),
                json.get("version").asInt(),
                json.get("pair").asText(),
                json.get("ownerData").asBoolean(),
                files,
                json.get("rootHash").asText());
        log.debug("dataset manifest id={} files={}", manifest.id(), files.size());
        return manifest;
    }

    /** The stored manifest of the dataset folder bundled on the test classpath under {@code /eval/datasets/<id>}. */
    static DatasetManifest loadBundled(final String id) {
        try {
            return load(Path.of(Objects.requireNonNull(DatasetManifest.class.getResource("/eval/datasets/" + id), id)
                    .toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("dataset folder unreadable: " + id, e);
        }
    }

    /** The manifest of {@code folder} with every listed file hashed again (LF line ends) and the root hash rebuilt. */
    static DatasetManifest of(final Path folder) {
        final DatasetManifest stored = load(folder);
        final Path evalRoot = folder.toAbsolutePath().getParent().getParent();
        final List<FileHash> hashed = stored.files().stream()
                .map(f -> new FileHash(f.path(), hashFile(evalRoot.resolve(f.path()), f.path())))
                .sorted(Comparator.comparing(FileHash::path))
                .toList();
        final String lines =
                hashed.stream().map(f -> f.path() + "\t" + f.sha256() + "\n").reduce("", String::concat);
        return new DatasetManifest(
                stored.id(),
                stored.version(),
                stored.pair(),
                stored.ownerData(),
                hashed,
                sha256(lines.getBytes(StandardCharsets.UTF_8)));
    }

    // Text is hashed with LF line ends so a checkout with CR LF still matches; any other file (an image, an EPUB) by
    // its raw bytes, so its hash is what sha256sum prints.
    private static String hashFile(final Path file, final String listedPath) {
        try {
            final boolean text = TEXT_SUFFIXES.stream().anyMatch(listedPath::endsWith);
            log.trace("hashing dataset file path={} text={}", listedPath, text);
            if (!text) {
                return sha256(Files.readAllBytes(file));
            }
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            return sha256(content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("dataset file unreadable path={}", listedPath);
            throw new IllegalStateException("dataset file missing or unreadable: " + listedPath, e);
        }
    }

    private static JsonNode readJson(final Path file) {
        try {
            return MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("manifest unreadable: " + file, e);
        }
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
