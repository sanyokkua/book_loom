package ua.bookloom.archtest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The production classes of all eight modules, and nothing else.
 *
 * <p>The exclusion below is load-bearing. The violation fixtures deliberately live in {@code ua.bookloom.<module>}
 * packages — that is the only way to put them <em>in scope</em> of the rules they are meant to trip — and they
 * compile into this source set's own output directory, which sits on the same classpath the importer scans.
 * Without the filter, every rule would fail against its own fixtures and the suite could never be green.
 */
final class ProductionClasses {

    /** Gradle's output directory for the {@code archTest} source set, relative to any module's build dir. */
    private static final String ARCH_TEST_OUTPUT = "/classes/java/archTest/";

    private static final ImportOption EXCLUDE_FIXTURES = location -> !location.contains(ARCH_TEST_OUTPUT);

    private static final JavaClasses IMPORTED =
            new ClassFileImporter().withImportOption(EXCLUDE_FIXTURES).importPackages(ModuleGraph.ROOT_PACKAGE);

    private ProductionClasses() {
        // Import holder.
    }

    static JavaClasses imported() {
        return IMPORTED;
    }
}
