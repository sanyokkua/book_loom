package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.NamePolicy;

/**
 * The estimate a person sees before the names pass counts every call the pass sends (15i review): the names reading,
 * the batched review, the recurring terms and the renderings, not the names reading alone. The book is invented.
 */
class NamesPassEstimateTest extends NamesPassTestBase {

    // IF the estimate counted the names reading only, THEN the pass would send more calls than the person was told.
    @Test
    void estimate_seededBook_coversEveryCallThePassSends() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        final NamesEstimate estimate =
                Objects.requireNonNull(fixture.pass().estimate(PROJECT).data());
        final NamesPassReport report = succeeded(run(model()));

        assertThat(estimate.calls()).isGreaterThanOrEqualTo(report.calls());
        assertThat(estimate.minutesHigh()).isPositive();
    }
}
