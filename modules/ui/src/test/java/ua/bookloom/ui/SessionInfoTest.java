package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The session facts the detailed log names: merged in first-seen order, one line each, never longer than a summary. */
class SessionInfoTest {

    @Test
    void update_twoUpdates_keepsFirstSeenOrderAndTheLatestValue() {
        final SessionInfo session = new SessionInfo();

        session.update(ordered("book", "Kobzar.fb2", "format", "FB2"));
        session.update(ordered("model", "gemma4:e4b", "book", "Frankenstein.epub"));

        assertThat(SessionInfo.format(session.snapshot()))
                .isEqualTo("book=Frankenstein.epub format=FB2 model=gemma4:e4b");
    }

    @Test
    void update_valueWithLineBreaks_isKeptOnOneLine() {
        final SessionInfo session = new SessionInfo();

        session.update(Map.of("genre", "gothic\nnovel\r\n  of horror"));

        assertThat(session.snapshot()).containsEntry("genre", "gothic novel of horror");
    }

    @Test
    void update_overlongValue_isCutWithAnEllipsis() {
        final SessionInfo session = new SessionInfo();

        session.update(Map.of("genre", "x".repeat(130)));

        assertThat(session.snapshot().get("genre")).isEqualTo("x".repeat(120) + "…");
    }

    @Test
    void snapshot_beforeAnyUpdate_isEmpty() {
        assertThat(new SessionInfo().snapshot()).isEmpty();
    }

    private static Map<String, String> ordered(final String k1, final String v1, final String k2, final String v2) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
