package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.app.env.OsEnvironment;

/**
 * One lookup for every variable: the process environment first, then the operating system's own place for a user's
 * variables, read at call time; only presence is ever told to the form, the value only to the request headers.
 */
class SystemEnvironmentVariablesTest {

    private final Map<String, String> process = new HashMap<>();
    private final FakeOs os = new FakeOs();
    private final AtomicLong nanos = new AtomicLong();
    private final SystemEnvironmentVariables variables = new SystemEnvironmentVariables(process::get, os, nanos::get);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger resolverLog = (Logger) LoggerFactory.getLogger(SystemEnvironmentVariables.class);

    @BeforeEach
    void captureLog() {
        appender.start();
        resolverLog.addAppender(appender);
    }

    @AfterEach
    void releaseLog() {
        resolverLog.detachAppender(appender);
    }

    @Test
    void value_inProcessAndOs_processWinsAndOsIsNotAsked() {
        process.put("LMS_KEY", "from-shell");
        os.values.put("LMS_KEY", "from-launchctl");

        assertThat(variables.value("LMS_KEY")).isEqualTo("from-shell");
        assertThat(os.asked).isEmpty();
    }

    @Test
    void value_onlyInOs_isTheOsValue() {
        os.values.put("LMS_KEY", "from-launchctl");

        assertThat(variables.value("LMS_KEY")).isEqualTo("from-launchctl");
        assertThat(variables.isSet("LMS_KEY")).isTrue();
    }

    @Test
    void value_blankInProcess_fallsBackToOs() {
        process.put("LMS_KEY", "  ");
        os.values.put("LMS_KEY", "from-launchctl");

        assertThat(variables.value("LMS_KEY")).isEqualTo("from-launchctl");
    }

    @ParameterizedTest
    @CsvSource({"MISSING,-,false", "EMPTY,'',false", "BLANK,'   ',false", "SET,sk-secret,true"})
    void isSet_osValue_isTrueOnlyForNonBlankValue(final String name, final String osValue, final boolean expected) {
        if (!"-".equals(osValue)) {
            os.values.put(name, osValue);
        }

        assertThat(variables.isSet(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "$LMS_KEY", "${env:LMS_KEY}", "1KEY", "A B", "KEY;rm", "KEY\nX"})
    void value_invalidName_isUnsetAndTheOsIsNeverAsked(final String name) {
        assertThat(variables.value(name)).isNull();
        assertThat(variables.isSet(name)).isFalse();
        assertThat(os.asked).isEmpty();
    }

    // IF a key typed where its variable's name belongs were looked up, THEN it would reach a launchctl command line
    // and the debug log as a "name"; it is never looked up and never logged.
    @Test
    void value_keyTypedAsTheName_isNeverLookedUpOrLogged() {
        resolverLog.setLevel(Level.TRACE);

        final String value = variables.value("sk_live_51HabcDEF123ghiJKL456");

        resolverLog.setLevel(null);
        assertThat(value).isNull();
        assertThat(os.asked).isEmpty();
        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains("sk_live_"));
    }

    @Test
    void value_osReaderFails_isUnsetWithOneWarnAcrossCalls() {
        os.failure = new IllegalStateException("launchctl timed out");

        assertThat(variables.value("LMS_KEY")).isNull();
        nanos.addAndGet(Duration.ofSeconds(5).toNanos());
        assertThat(variables.isSet("LMS_KEY")).isFalse();

        assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.WARN))
                .hasSize(1);
    }

    @Test
    void value_setInOsAfterAnUnsetRead_isSeenOnceTheShortCacheExpires() {
        assertThat(variables.isSet("LMS_KEY")).isFalse();
        os.values.put("LMS_KEY", "set-later");

        final boolean withinCache = variables.isSet("LMS_KEY");
        nanos.addAndGet(Duration.ofSeconds(3).toNanos());

        assertThat(withinCache).isFalse();
        assertThat(variables.value("LMS_KEY")).isEqualTo("set-later");
    }

    @Test
    void value_readTwiceWithinTheCache_asksTheOsOnce() {
        os.values.put("LMS_KEY", "from-launchctl");

        variables.value("LMS_KEY");
        variables.isSet("LMS_KEY");

        assertThat(os.asked).containsExactly("LMS_KEY");
    }

    @Test
    void value_found_neverWritesTheValueToTheLog() {
        os.values.put("LMS_KEY", "sk-very-secret");
        resolverLog.setLevel(Level.TRACE);

        variables.value("LMS_KEY");

        resolverLog.setLevel(null);
        assertThat(appender.list)
                .isNotEmpty()
                .noneMatch(event -> event.getFormattedMessage().contains("sk-very-secret"));
    }

    /** A hand-written operating-system source: a map, the names it was asked for, and an optional failure. */
    private static final class FakeOs implements OsEnvironment {

        private final Map<String, String> values = new HashMap<>();
        private final List<String> asked = new ArrayList<>();
        private @Nullable RuntimeException failure;

        @Override
        public Optional<String> read(final String name) {
            asked.add(name);
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(values.get(name));
        }

        @Override
        public String source() {
            return "fake";
        }
    }
}
