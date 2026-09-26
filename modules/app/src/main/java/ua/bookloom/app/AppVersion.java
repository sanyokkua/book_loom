package ua.bookloom.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Reads the build-generated version string.
 *
 * <p>The value is written into {@code ua/bookloom/app/version.properties} by the {@code generateVersionResource}
 * Gradle task, from the {@code appVersion} property the release pipeline passes. A build that does not pass it
 * reports {@code dev} — and that fallback is a feature, not a degradation: it is how an artifact says "I did not
 * come from the release pipeline", which is the claim EC-REL-7 exists to catch.
 *
 * <p>A classpath resource rather than the jar manifest's {@code Implementation-Version}, because the manifest value
 * is {@code null} whenever the code runs from class directories — which is how {@code ./gradlew :app:run}, the
 * tests, and every IDE launch execute.
 *
 * <p>DD-50 permits exactly two consumers: the startup log line, and the About dialog. The dialog receives the value
 * through the {@code @BuildVersion} binding {@code AppModule} makes from {@link #current()}, so there is still one
 * reader and no second copy of the string — a second copy is how a displayed version drifts from the tag that
 * produced it.
 */
public final class AppVersion {

    /** Absolute so the lookup does not depend on the calling class's package. */
    private static final String RESOURCE = "/ua/bookloom/app/version.properties";

    private static final String KEY = "version";

    /** Reported when the resource is absent or unreadable: this build did not come from the release pipeline. */
    public static final String FALLBACK = "dev";

    private AppVersion() {
        // Static reader only.
    }

    /**
     * The version of this build.
     *
     * @return the injected version, or {@code dev} when none was injected
     */
    public static String current() {
        try (InputStream stream = AppVersion.class.getResourceAsStream(RESOURCE)) {
            return read(stream);
        } catch (IOException e) {
            // A version string is never worth failing a startup over, and this runs before there is much to log to.
            return FALLBACK;
        }
    }

    /**
     * Parses the resource's contents.
     *
     * <p>Package-private so the absent-resource and malformed-resource paths can be exercised without a second
     * build: the production lookup always finds the file this build's own task wrote.
     *
     * @param stream the resource contents, or {@code null} when the resource does not exist
     * @return the version, or {@code dev} when absent or blank
     */
    static String read(@org.jspecify.annotations.Nullable InputStream stream) {
        if (stream == null) {
            return FALLBACK;
        }
        final Properties properties = new Properties();
        try {
            properties.load(stream);
        } catch (IOException e) {
            return FALLBACK;
        }
        final String version = properties.getProperty(KEY, FALLBACK);
        return Objects.requireNonNull(version).isBlank() ? FALLBACK : version.trim();
    }
}
