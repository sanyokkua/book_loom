package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.FixtureCatalog;

/**
 * Proves the catalogue's {@code coverageFloor}s stay honest against the corrected {@link TextCoverage} metric —
 * the meta-test the "declared floor is not left slack" scenario asks for.
 *
 * <p>{@link FixtureSweepTest} already proves every fixture's measured coverage is at or above its declared floor.
 * What it does not prove is that the floor is <em>close</em> to the measurement: a floor of {@code 0.0} would pass
 * that check against any fixture whatsoever and would never catch a real regression. This class adds the other
 * half — the declared floor is no more than {@code 0.02} below what the corrected metric actually reports —
 * so a floor can no longer silently absorb a shortfall the way the pre-fix Markdown floor of {@code 0.90} did
 * against a metric that measured real files at {@code 0.853}-{@code 0.956}.
 *
 * <p><strong>{@code md/only-a-fence} is carved out of the slack check, not exempted from the floor check.</strong>
 * It declares a floor of {@code 0.0} because it has no translatable text at all, and {@link TextCoverage#of}
 * defines an empty denominator as fully covered ({@code 1.0}) — the floor is not measuring segmentation quality
 * on that fixture, so a {@code 1.0}-vs-{@code 0.0} gap there is not the slack this scenario exists to close. It
 * still has to clear its own floor, which {@link #everyFixture_measuredCoverageMeetsItsDeclaredFloor} checks
 * unconditionally.
 */
class CoverageFloorMetaTest {

    @TempDir
    private Path tempDir;

    private static List<FixtureCatalog.Case> everyFixture() {
        return FixtureCatalog.all();
    }

    /** Every fixture whose floor is a real measurement of segmentation quality, per the class Javadoc's carve-out. */
    private static List<FixtureCatalog.Case> fixturesWithAMeasuredFloor() {
        return FixtureCatalog.all().stream()
                .filter(fixture -> fixture.coverageFloor() > 0.0)
                .toList();
    }

    // Covers: FR-DOC-09 — WHEN each fixture's coverage is measured and compared against the floor that fixture
    // declares, THEN for every fixture the measured coverage is at or above its declared floor.
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFixture")
    void everyFixture_measuredCoverageMeetsItsDeclaredFloor(FixtureCatalog.Case fixture) {
        assertThat(measuredCoverageOf(fixture)).isGreaterThanOrEqualTo(fixture.coverageFloor());
    }

    // Covers: FR-DOC-09 — AND the declared floor is no more than 0.02 below the measured coverage, so a floor
    // cannot silently absorb a real regression.
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesWithAMeasuredFloor")
    void everyMeasuredFixture_declaredFloorIsWithinSlackOfMeasuredCoverage(FixtureCatalog.Case fixture) {
        assertThat(measuredCoverageOf(fixture) - fixture.coverageFloor()).isLessThanOrEqualTo(0.02);
    }

    private double measuredCoverageOf(FixtureCatalog.Case fixture) {
        final Path source =
                fixture.builder().apply(tempDir.resolve(fixture.name().replace('/', '-') + "-" + fixture.fileName()));
        final Result<Document> opened = DocumentServices.newService().open(source);
        final Document document =
                Objects.requireNonNull(opened.data(), () -> fixture.name() + " did not open: " + opened.error());
        return TextCoverage.of(source, fixture.format(), document);
    }
}
