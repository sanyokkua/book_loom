package ua.bookloom.util.paths;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.util.paths.AppPathsResolver.PathLayout;

/**
 * The per-OS resolution matrix and the seven resolver-owned edge cases.
 *
 * <p>Two of the nine {@code EC-ENV-*} cases are deliberately absent. <strong>EC-ENV-3</strong> (a second instance) is
 * a lock concern and is proved against the launcher, not here. <strong>EC-ENV-5</strong> ({@code ATOMIC_MOVE}
 * unsupported) describes the atomic file-replace used for exports, which this change does not ship — a test for it
 * would assert against code that does not exist.
 *
 * <p>The layout cases assert <em>strings</em> rather than {@link Path}s, and that is not a shortcut. On this JVM
 * {@code Path.of("C:\\Users\\ok")} is a relative path, so a Windows expectation expressed as a {@code Path} could
 * only ever be checked on Windows. Asserting the decision before it becomes a {@code Path} is what makes all three
 * operating systems provable from one machine; {@link HostPlatform} then covers the conversion on whichever platform
 * is really running.
 */
class AppPathsResolverTest {

    private static final String WINDOWS = "Windows 11";
    private static final String MACOS = "Mac OS X";
    private static final String LINUX = "Linux";

    private static PathLayout layout(final Map<String, String> env, final Map<String, String> properties) {
        final Result<PathLayout> result = new AppPathsResolver(env::get, properties::get).layout();
        assertThat(result.isOk())
                .as("expected a resolved layout but got: %s", result.error())
                .isTrue();
        return dataOf(result);
    }

    /**
     * Unwraps a success. {@code Result.data()} is {@code @Nullable} by construction, so NullAway rightly refuses to
     * dereference it after an {@code isOk()} check it cannot see through — the explicit unwrap is the honest way to
     * say "the assertion above established this".
     */
    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "data");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static Map<String, String> properties(final String osName, final String... extra) {
        final Map<String, String> properties = new HashMap<>();
        properties.put("os.name", osName);
        for (int index = 0; index < extra.length; index += 2) {
            properties.put(extra[index], extra[index + 1]);
        }
        return properties;
    }

    // ===== The per-OS matrix, production =========================================================================

    // The system SHALL place the database under the per-OS application data directory and
    // the logs under the per-OS log directory.
    @Test
    void layout_windowsProduction_usesLocalAppDataWithALogsChild() {
        final PathLayout resolved = layout(
                Map.of("LOCALAPPDATA", "C:\\Users\\ok\\AppData\\Local", "BOOKLOOM_ENV", "prod"), properties(WINDOWS));

        assertThat(resolved.dataDir()).isEqualTo("C:\\Users\\ok\\AppData\\Local\\BookLoom");
        assertThat(resolved.logDir()).isEqualTo("C:\\Users\\ok\\AppData\\Local\\BookLoom\\logs");
    }

    // The system SHALL place the database under the per-OS application data directory and
    // the logs under the per-OS log directory.
    @Test
    void layout_macOsProduction_separatesApplicationSupportFromLibraryLogs() {
        final PathLayout resolved = layout(Map.of("BOOKLOOM_ENV", "prod"), properties(MACOS, "user.home", "/Users/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/Users/ok/Library/Application Support/BookLoom");
        assertThat(resolved.logDir())
                .as("macOS keeps logs in ~/Library/Logs, not inside the data folder")
                .isEqualTo("/Users/ok/Library/Logs/BookLoom");
    }

    // The system SHALL place the database under the per-OS application data directory and
    // the logs under the per-OS log directory.
    @Test
    void layout_linuxProduction_usesXdgDataForDataAndXdgStateForLogs() {
        final PathLayout resolved = layout(Map.of("BOOKLOOM_ENV", "prod"), properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/home/ok/.local/share/bookloom");
        assertThat(resolved.logDir())
                .as("the XDG base-directory spec puts logs under the state dir, not the data dir")
                .isEqualTo("/home/ok/.local/state/bookloom/logs");
    }

    @Test
    void layout_linuxExplicitXdgDirs_honoursBothOfThem() {
        final PathLayout resolved = layout(
                Map.of("BOOKLOOM_ENV", "prod", "XDG_DATA_HOME", "/data", "XDG_STATE_HOME", "/state"),
                properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/data/bookloom");
        assertThat(resolved.logDir()).isEqualTo("/state/bookloom/logs");
    }

    // ===== The dev/production split (EC-ENV-4) ===================================================================

    // WHERE the run is a development build, the system SHALL resolve every path under a
    // separate `-Dev`/`-dev` sibling folder so production data is never read or written.
    @Test
    void layout_windowsDevelopment_usesTheDevSiblingFolder() {
        final PathLayout resolved =
                layout(Map.of("LOCALAPPDATA", "C:\\Users\\ok\\AppData\\Local"), properties(WINDOWS));

        assertThat(resolved.dataDir()).isEqualTo("C:\\Users\\ok\\AppData\\Local\\BookLoom-Dev");
    }

    // WHERE the run is a development build, the system SHALL resolve every path under a
    // separate `-Dev`/`-dev` sibling folder so production data is never read or written.
    @Test
    void layout_macOsDevelopment_usesTheDevSiblingForBothDataAndLogs() {
        final PathLayout resolved = layout(Map.of(), properties(MACOS, "user.home", "/Users/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/Users/ok/Library/Application Support/BookLoom-Dev");
        assertThat(resolved.logDir()).isEqualTo("/Users/ok/Library/Logs/BookLoom-Dev");
    }

    // WHERE the run is a development build, the system SHALL resolve every path under a
    // separate `-Dev`/`-dev` sibling folder so production data is never read or written.
    @Test
    void layout_linuxDevelopment_usesTheLowercaseDevSibling() {
        final PathLayout resolved = layout(Map.of(), properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/home/ok/.local/share/bookloom-dev");
        assertThat(resolved.logDir()).isEqualTo("/home/ok/.local/state/bookloom-dev/logs");
    }

    /**
     * EC-ENV-4 stated as the property that actually matters: because the whole folder differs, the two environments
     * hold different lock files and can therefore run at the same time. Asserting "the names differ" would be weaker
     * — it is the <em>lock path</em> divergence that makes simultaneous runs safe.
     */
    // WHERE a development and a production build run at once, the system SHALL keep their
    // data folders — and therefore their single-instance locks — entirely separate.
    @Test
    void layout_devAndProduction_resolveToDisjointFolders() {
        final Map<String, String> properties = properties(LINUX, "user.home", "/home/ok");

        final PathLayout dev = layout(Map.of(), properties);
        final PathLayout prod = layout(Map.of("BOOKLOOM_ENV", "prod"), properties);

        assertThat(dev.dataDir()).isNotEqualTo(prod.dataDir());

        // Compared as PATHS, not as strings. "bookloom-dev" does start with "bookloom" textually, which is exactly
        // the trap: a string-prefix check would report the dev folder as living inside the production one. What has
        // to be true is that neither directory contains the other, so neither run can reach the other's lock or
        // database.
        final Path devDir = Path.of(dev.dataDir());
        final Path prodDir = Path.of(prod.dataDir());
        assertThat(devDir.startsWith(prodDir)).isFalse();
        assertThat(prodDir.startsWith(devDir)).isFalse();
        assertThat(devDir.getParent()).isEqualTo(prodDir.getParent());
    }

    // ===== EC-ENV-1 — blank or relative XDG values ===============================================================

    // IF an XDG base-directory variable is blank or relative, THEN the system SHALL ignore
    // it and use the ~/.local fallback rather than resolving against the working directory.
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "relative/path", "./data", "~/data"})
    void layout_blankOrRelativeXdgDataHome_fallsBackToDotLocalShare(final String value) {
        final PathLayout resolved = layout(
                Map.of("BOOKLOOM_ENV", "prod", "XDG_DATA_HOME", value, "XDG_STATE_HOME", value),
                properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/home/ok/.local/share/bookloom");
        assertThat(resolved.logDir()).isEqualTo("/home/ok/.local/state/bookloom/logs");
    }

    // ===== EC-ENV-6 — the Windows fallback chain =================================================================

    // IF %LOCALAPPDATA% is unset, THEN the system SHALL fall back to %USERPROFILE%\AppData\
    // Local and then to user.home\AppData\Local rather than failing to resolve.
    @Test
    void layout_windowsLocalAppDataUnset_fallsBackToUserProfile() {
        final PathLayout resolved =
                layout(Map.of("USERPROFILE", "C:\\Users\\ok", "BOOKLOOM_ENV", "prod"), properties(WINDOWS));

        assertThat(resolved.dataDir()).isEqualTo("C:\\Users\\ok\\AppData\\Local\\BookLoom");
    }

    // IF %LOCALAPPDATA% is unset, THEN the system SHALL fall back to %USERPROFILE%\AppData\
    // Local and then to user.home\AppData\Local rather than failing to resolve.
    @Test
    void layout_windowsLocalAppDataAndUserProfileUnset_fallsBackToUserHome() {
        final PathLayout resolved =
                layout(Map.of("BOOKLOOM_ENV", "prod"), properties(WINDOWS, "user.home", "C:\\Users\\ok"));

        assertThat(resolved.dataDir()).isEqualTo("C:\\Users\\ok\\AppData\\Local\\BookLoom");
    }

    @Test
    void layout_windowsBlankLocalAppData_isIgnoredLikeAnUnsetOne() {
        final PathLayout resolved = layout(
                Map.of("LOCALAPPDATA", "   ", "USERPROFILE", "C:\\Users\\ok", "BOOKLOOM_ENV", "prod"),
                properties(WINDOWS));

        assertThat(resolved.dataDir()).isEqualTo("C:\\Users\\ok\\AppData\\Local\\BookLoom");
    }

    @Test
    void layout_windowsUncLocalAppData_isAcceptedAsAbsolute() {
        final PathLayout resolved =
                layout(Map.of("LOCALAPPDATA", "\\\\server\\share", "BOOKLOOM_ENV", "prod"), properties(WINDOWS));

        assertThat(resolved.dataDir()).isEqualTo("\\\\server\\share\\BookLoom");
    }

    // ===== EC-ENV-7 — no usable home =============================================================================

    // IF no usable home directory exists on any platform, THEN the system SHALL return a
    // validation failure and refuse to start rather than writing to the working directory.
    @ParameterizedTest
    @ValueSource(strings = {MACOS, LINUX, WINDOWS})
    void layout_noUsableHomeAnywhere_isAValidationFailure(final String osName) {
        final Result<PathLayout> result =
                new AppPathsResolver(Map.<String, String>of()::get, properties(osName)::get).layout();

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(errorOf(result).retryable()).isFalse();
        assertThat(errorOf(result).message())
                .as("the message must tell the user the sanctioned escape hatch, not merely that it failed")
                .contains("BOOKLOOM_DATA_DIR");
    }

    @Test
    void layout_blankUserHome_isTreatedAsNoHomeAtAll() {
        final Result<PathLayout> result =
                new AppPathsResolver(Map.<String, String>of()::get, properties(LINUX, "user.home", "  ")::get).layout();

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // ===== EC-ENV-9 — the BOOKLOOM_DATA_DIR override =============================================================

    // WHEN BOOKLOOM_DATA_DIR is set to an absolute path, the system SHALL use it verbatim as
    // the data directory and derive the log directory beneath it.
    @Test
    void layout_absoluteOverride_takesItVerbatimAndDerivesLogsBeneathIt() {
        final PathLayout resolved = layout(
                Map.of("BOOKLOOM_DATA_DIR", "/volumes/books/bookloom"), properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/volumes/books/bookloom");
        assertThat(resolved.logDir()).isEqualTo("/volumes/books/bookloom/logs");
    }

    /**
     * The override deliberately does <em>not</em> gain the {@code -dev} suffix: it is an explicit instruction to use
     * one folder, and silently redirecting it to a sibling would defeat the reason it exists — the packaging launch
     * smoke pointing at a temp directory needs the folder it named, not a variant of it.
     */
    @Test
    void layout_absoluteOverrideInDevelopment_isNotGivenTheDevSuffix() {
        final PathLayout resolved =
                layout(Map.of("BOOKLOOM_DATA_DIR", "/tmp/bookloom-smoke"), properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/tmp/bookloom-smoke");
    }

    // IF BOOKLOOM_DATA_DIR is blank or relative, THEN the system SHALL ignore it and apply
    // normal per-OS resolution.
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "books", "./books", "../books"})
    void layout_blankOrRelativeOverride_isIgnoredAndPerOsResolutionApplies(final String value) {
        final PathLayout resolved = layout(
                Map.of("BOOKLOOM_DATA_DIR", value, "BOOKLOOM_ENV", "prod"), properties(LINUX, "user.home", "/home/ok"));

        assertThat(resolved.dataDir()).isEqualTo("/home/ok/.local/share/bookloom");
    }

    // ===== Conversion, creation and probing on whatever platform is really running ================================

    /**
     * Everything above proves the decision. These prove the parts that can only be exercised against a real
     * filesystem, so they run against the host platform through {@code BOOKLOOM_DATA_DIR} rather than simulating an
     * operating system.
     */
    @Nested
    class HostPlatform {

        @TempDir
        private Path tempDir;

        private AppPathsResolver resolverFor(final Path dataDir) {
            return new AppPathsResolver(Map.of("BOOKLOOM_DATA_DIR", dataDir.toString())::get, System::getProperty);
        }

        @Test
        void resolve_override_derivesTheLockAndDatabasePathsInsideTheDataDir() {
            final Result<AppPaths> result = resolverFor(tempDir.resolve("data")).resolve();

            assertThat(result.isOk()).isTrue();
            final AppPaths paths = dataOf(result);
            assertThat(paths.lockFile()).isEqualTo(paths.dataDir().resolve("bookloom.lock"));
            assertThat(paths.databaseFile()).isEqualTo(paths.dataDir().resolve("bookloom.db"));
            assertThat(paths.logDir()).isEqualTo(paths.dataDir().resolve("logs"));
        }

        // WHEN the data or log directory does not exist on first run, the system SHALL
        // create it idempotently before use.
        @Test
        void prepare_firstRun_createsBothDirectoriesAndIsIdempotent() {
            final AppPathsResolver resolver = resolverFor(tempDir.resolve("data"));
            final AppPaths paths = dataOf(resolver.resolve());

            assertThat(resolver.prepare(paths).isOk()).isTrue();
            assertThat(paths.dataDir()).isDirectory();
            assertThat(paths.logDir()).isDirectory();

            assertThat(resolver.prepare(paths).isOk())
                    .as("a second start must not fail because the folders already exist")
                    .isTrue();
        }

        // IF the data or log directory cannot be created, THEN the system SHALL return a
        // validation failure rather than starting without a usable data directory.
        @Test
        void prepare_directoryCannotBeCreated_isAValidationFailure() throws IOException {
            // A regular file where a directory must go fails createDirectories on every platform, without needing
            // permission games that behave differently as root or on Windows.
            final Path blocker = tempDir.resolve("blocker");
            Files.writeString(blocker, "not a directory");

            final AppPathsResolver resolver = resolverFor(blocker.resolve("data"));
            final Result<AppPaths> prepared = resolver.resolve().flatMap(resolver::prepare);

            assertThat(prepared.isErr()).isTrue();
            assertThat(errorOf(prepared).code()).isEqualTo(ErrorCode.validation);
            assertThat(errorOf(prepared).cause()).isInstanceOf(IOException.class);
        }

        /**
         * EC-ENV-8's negative half, which is the half that can be asserted without a network share: a local
         * directory is not flagged. The positive half — a share being detected — cannot be provoked in a hermetic
         * test, so {@link AppPathsTest} covers the flag's propagation instead, and the warning it drives is asserted
         * against the launcher.
         */
        @Test
        void prepare_localTempDirectory_isNotFlaggedAsNetworkFilesystem() {
            final AppPathsResolver resolver = resolverFor(tempDir.resolve("data"));

            final Result<AppPaths> prepared = resolver.resolve().flatMap(resolver::prepare);

            assertThat(prepared.isOk()).isTrue();
            assertThat(dataOf(prepared).onNetworkFilesystem()).isFalse();
        }
    }
}
