package ua.bookloom.app.env;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Each operating system's own place for a user's variables, read through a fake command runner or real temp files. */
class OsEnvironmentTest {

    private static final String HKLM = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";

    private final List<List<String>> commands = new ArrayList<>();

    private CommandRunner answering(final Map<String, CommandRunner.Output> byHive) {
        return command -> {
            commands.add(command);
            return byHive.getOrDefault(command.get(2), new CommandRunner.Output(1, ""));
        };
    }

    @ParameterizedTest
    @CsvSource({
        "Mac OS X, launchctl",
        "Windows 11, windows-registry",
        "Linux, environment-files",
        "FreeBSD, environment-files"
    })
    void forOs_eachSystem_choosesItsSource(final String osName, final String expected) {
        final OsEnvironment source = OsEnvironment.forOs(osName, name -> null, name -> "/home/reader", command -> {
            throw new AssertionError("no command is run to choose");
        });

        assertThat(source.source()).isEqualTo(expected);
    }

    @Test
    void launchctl_variableSet_returnsItsValueWithoutTheLineEnd() {
        final OsEnvironment launchctl = new LaunchctlEnvironment(command -> {
            commands.add(command);
            return new CommandRunner.Output(0, "sk-abc def\n");
        });

        assertThat(launchctl.read("LMS_KEY")).contains("sk-abc def");
        assertThat(commands).containsExactly(List.of("launchctl", "getenv", "LMS_KEY"));
    }

    @ParameterizedTest
    @CsvSource({"0,''", "0,'\n'", "1,'whatever\n'"})
    void launchctl_unsetOrFailingExit_isEmpty(final int exit, final String output) {
        final OsEnvironment launchctl = new LaunchctlEnvironment(command -> new CommandRunner.Output(exit, output));

        assertThat(launchctl.read("LMS_KEY")).isEmpty();
    }

    @Test
    void launchctl_runnerFails_passesTheFailureOn() {
        final OsEnvironment launchctl = new LaunchctlEnvironment(command -> {
            throw new UncheckedIOException(new IOException("timed out"));
        });

        assertThatThrownBy(() -> launchctl.read("LMS_KEY")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void registry_userValue_winsAndTheMachineIsNotAsked() {
        final OsEnvironment registry = new WindowsRegistryEnvironment(answering(Map.of(
                "HKCU\\Environment",
                new CommandRunner.Output(
                        0, "\r\nHKEY_CURRENT_USER\\Environment\r\n    LMS_KEY    REG_SZ    sk user\r\n\r\n"))));

        assertThat(registry.read("LMS_KEY")).contains("sk user");
        assertThat(commands).containsExactly(List.of("reg", "query", "HKCU\\Environment", "/v", "LMS_KEY"));
    }

    @Test
    void registry_onlyMachineValue_isTheMachineValue() {
        final OsEnvironment registry = new WindowsRegistryEnvironment(answering(Map.of(
                HKLM,
                new CommandRunner.Output(
                        0, "\r\nHKEY_LOCAL_MACHINE\\...\r\n    lms_key    REG_EXPAND_SZ    sk-machine\r\n"))));

        assertThat(registry.read("LMS_KEY")).contains("sk-machine");
        assertThat(commands).hasSize(2);
    }

    @Test
    void registry_emptyValueAndMissing_isEmpty() {
        final OsEnvironment registry = new WindowsRegistryEnvironment(answering(Map.of(
                "HKCU\\Environment",
                new CommandRunner.Output(0, "\r\nHKEY_CURRENT_USER\\Environment\r\n    LMS_KEY    REG_SZ\r\n"))));

        assertThat(registry.read("LMS_KEY")).isEmpty();
    }

    @Test
    void files_laterEnvironmentDFileWinsAndQuotesAreRemoved(@TempDir final Path home) throws IOException {
        final Path dir = Files.createDirectories(home.resolve(".config/environment.d"));
        write(dir.resolve("10-first.conf"), "# a comment\nLMS_KEY=first\n");
        write(dir.resolve("50-bookloom.conf"), "OTHER=x\nexport LMS_KEY = \"sk later\"\n");
        write(dir.resolve("90-ignored.txt"), "LMS_KEY=not-a-conf-file\n");

        final OsEnvironment files = new EnvironmentFiles(dir, home.resolve("etc-environment"));

        assertThat(files.read("LMS_KEY")).contains("sk later");
    }

    @Test
    void files_onlyEtcEnvironment_isItsSingleQuotedValue(@TempDir final Path home) throws IOException {
        final Path etc = home.resolve("environment");
        write(etc, "PATH=\"/usr/bin\"\nLMS_KEY='sk-etc'\n");

        final OsEnvironment files = new EnvironmentFiles(home.resolve("missing.d"), etc);

        assertThat(files.read("LMS_KEY")).contains("sk-etc");
        assertThat(files.read("LMS")).isEmpty();
    }

    @Test
    void files_nothingAnywhere_isEmpty(@TempDir final Path home) {
        final OsEnvironment files = new EnvironmentFiles(home.resolve("missing.d"), home.resolve("missing"));

        assertThat(files.read("LMS_KEY")).isEmpty();
    }

    @Test
    void forOs_linuxWithXdgConfigHome_readsItsEnvironmentD(@TempDir final Path home) throws IOException {
        final Path dir = Files.createDirectories(home.resolve("xdg/environment.d"));
        write(dir.resolve("50-bookloom.conf"), "LMS_KEY=from-xdg\n");

        final OsEnvironment source = OsEnvironment.forOs(
                "Linux",
                name -> "XDG_CONFIG_HOME".equals(name) ? home.resolve("xdg").toString() : null,
                name -> home.toString(),
                command -> new CommandRunner.Output(1, ""));

        assertThat(source.read("LMS_KEY")).contains("from-xdg");
    }

    private static void write(final Path file, final String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
