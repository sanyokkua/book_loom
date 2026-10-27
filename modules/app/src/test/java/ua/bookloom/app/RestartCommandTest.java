package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The next copy is started the way this one was: same command, same arguments, nothing guessed. */
class RestartCommandTest {

    @Test
    void of_commandAndArguments_sameCommandThenArguments() {
        assertThat(RestartCommand.of(FakeProcessInfo.of("/usr/bin/java", "-jar", "app.jar")))
                .contains(List.of("/usr/bin/java", "-jar", "app.jar"));
    }

    @Test
    void of_launcherWithoutArguments_commandAlone() {
        assertThat(RestartCommand.of(
                        FakeProcessInfo.withoutArguments("/Applications/BookLoom.app/Contents/MacOS/BookLoom")))
                .contains(List.of("/Applications/BookLoom.app/Contents/MacOS/BookLoom"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/usr/bin/java", "C:\\jdk\\bin\\JAVAW.EXE", "java"})
    void of_javaWithoutArguments_empty(final String command) {
        assertThat(RestartCommand.of(FakeProcessInfo.withoutArguments(command))).isEmpty();
    }

    @Test
    void of_javaWithEmptyArguments_empty() {
        assertThat(RestartCommand.of(FakeProcessInfo.of("/usr/bin/java"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", ""})
    void of_blankCommand_empty(final String command) {
        assertThat(RestartCommand.of(FakeProcessInfo.of(command, "x"))).isEmpty();
    }

    @Test
    void of_noCommand_empty() {
        assertThat(RestartCommand.of(FakeProcessInfo.of(null))).isEmpty();
    }

    @Test
    void of_argumentWithSpaces_keptAsOne() {
        assertThat(RestartCommand.of(FakeProcessInfo.of("/usr/bin/java", "-Dname=a b", "-jar", "my app.jar")))
                .contains(List.of("/usr/bin/java", "-Dname=a b", "-jar", "my app.jar"));
    }
}
