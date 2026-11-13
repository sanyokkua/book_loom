package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MiniLabelsTest {

    private static MiniLabels.Label label(final String difficulty, final String defectClass) {
        return new MiniLabels.Label("translate", "en-uk", difficulty, "11-30", defectClass);
    }

    @Test
    void labels_valueWithFewerThanEightCases_isNotInterpretable() {
        final Map<String, MiniLabels.Label> byBlock = new LinkedHashMap<>();
        IntStream.rangeClosed(1, 8).forEach(i -> byBlock.put("a" + i, label("easy", "brand")));
        IntStream.rangeClosed(1, 7).forEach(i -> byBlock.put("b" + i, label("hard", "negation")));
        final MiniLabels labels = new MiniLabels(byBlock);

        final Map<String, Boolean> allUsable = new LinkedHashMap<>();
        byBlock.keySet().forEach(id -> allUsable.put(id, true));
        final String text = labels.describe(allUsable);

        assertThat(text).contains("difficulty=easy: 8 cases, 8 usable");
        assertThat(text).contains("difficulty=hard: 7 cases, not interpretable");
        assertThat(text).contains("defectClass=negation: 7 cases, not interpretable");
    }
}
