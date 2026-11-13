package ua.bookloom.archtest;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    /**
     * The production classes whose class file holds any of the names. A class's constant pool holds every string it
     * could pass to {@code System.getenv} or {@code System.getProperty}, so a class file without the name cannot read it.
     *
     * @param names the retired switch names; never null
     * @return the names of the classes that hold one; never null, empty when none does
     */
    static List<String> naming(final List<String> names) {
        return IMPORTED.stream()
                .filter(javaClass -> names.stream().anyMatch(classFileText(javaClass)::contains))
                .map(JavaClass::getName)
                .toList();
    }

    // Read as ISO-8859-1 so every byte maps to one character and the UTF-8 constants stay findable as text.
    private static String classFileText(final JavaClass javaClass) {
        final URI location = javaClass.getSource().orElseThrow().getUri();
        try (InputStream in = location.toURL().openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
