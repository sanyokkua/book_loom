package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.prompt.DraftContext;

class BatchContextsTest {

    private static DraftContext context(@Nullable final String summary, final String... glossary) {
        return new DraftContext(List.of(), summary, List.of(glossary), List.of("He left. → Він пішов."));
    }

    @Test
    void of_lockedNameLines_arePrefixedWithTheirItemIdAndOthersAreShared() {
        final BatchContext joined = BatchContexts.of(
                List.of("1", "2"),
                List.of(
                        context("Hale searches.", "Hale → Гейл (character, male)", "⟦g0⟧ → Бартімеус, person, male"),
                        context("Hale searches.", "Hale → Гейл (character, male)", "⟦g0⟧ → Бартімеус, person, male")),
                List.of(),
                "",
                List.of());

        assertThat(joined.draft().glossaryLines())
                .containsExactly(
                        "Hale → Гейл (character, male)",
                        "1: ⟦g0⟧ → Бартімеус, person, male",
                        "2: ⟦g0⟧ → Бартімеус, person, male");
        assertThat(joined.draft().summary()).isEqualTo("Hale searches.");
        assertThat(joined.draft().memoryLines()).containsExactly("He left. → Він пішов.");
    }

    @Test
    void of_summaryOnlyOnALaterItem_isTaken() {
        final BatchContext joined = BatchContexts.of(
                List.of("1", "2"), List.of(context(null), context("Later summary.")), List.of(), "", List.of());

        assertThat(joined.draft().summary()).isEqualTo("Later summary.");
    }

    @Test
    void of_contextsNotMatchingTheIds_isRejected() {
        assertThatThrownBy(
                        () -> BatchContexts.of(List.of("1", "2"), List.of(context(null)), List.of(), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
