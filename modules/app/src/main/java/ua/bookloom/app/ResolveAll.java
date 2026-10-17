package ua.bookloom.app;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;

/**
 * The packaged image's own proof that its module graph is whole: it loads every screen and resolves every port once,
 * which a classpath launch cannot fail and a first use would only find later. A missing {@code opens}, {@code exports}
 * or resource shows up here as a named screen or port, in one log line the launch smoke reads.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ResolveAll {

    /** The system property that turns the step on (and builds the injector in {@code Stage.PRODUCTION}). */
    static final String PROPERTY = "bookloom.smoke.resolveAll";

    private static final String API_PACKAGE = "ua.bookloom.api";

    /** Shows a screen and says which one is current afterwards. */
    @FunctionalInterface
    interface Navigation {
        Optional<ViewNames> show(ViewNames view);
    }

    /**
     * What the step found.
     *
     * @param loadedViews names of the screens that became current
     * @param resolvedPorts simple names of the ports that resolved
     * @param failures one entry per screen or port that did not, each naming it
     */
    record Outcome(List<String> loadedViews, List<String> resolvedPorts, List<String> failures) {

        Outcome {
            loadedViews = List.copyOf(loadedViews);
            resolvedPorts = List.copyOf(resolvedPorts);
            failures = List.copyOf(failures);
        }

        boolean isPassed() {
            return failures.isEmpty();
        }

        Outcome merge(final Outcome other) {
            final List<String> views = new ArrayList<>(loadedViews);
            views.addAll(other.loadedViews);
            final List<String> ports = new ArrayList<>(resolvedPorts);
            ports.addAll(other.resolvedPorts);
            final List<String> failed = new ArrayList<>(failures);
            failed.addAll(other.failures);
            return new Outcome(views, ports, failed);
        }
    }

    static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY));
    }

    static Navigation navigation(final Navigator navigator) {
        Objects.requireNonNull(navigator, "navigator");
        return view -> {
            navigator.navigate(view);
            return Optional.ofNullable(navigator.currentView().get());
        };
    }

    /** Runs both halves on the FX thread, logs the one result line and returns it. */
    static Outcome run(final Injector injector, final Navigator navigator, final ViewNames initial) {
        final Outcome outcome = check(injector, navigation(navigator), initial);
        if (outcome.isPassed()) {
            log.info(
                    "resolve-all passed: views={} ports={}",
                    outcome.loadedViews().size(),
                    outcome.resolvedPorts().size());
        } else {
            log.error("resolve-all FAILED: {}", String.join("; ", outcome.failures()));
        }
        return outcome;
    }

    static Outcome check(final Injector injector, final Navigation navigation, final ViewNames initial) {
        Objects.requireNonNull(injector, "injector");
        Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(initial, "initial");
        log.debug("resolve-all starting, initial view {}", initial);
        return loadViews(navigation, initial).merge(resolvePorts(injector));
    }

    static Outcome loadViews(final Navigation navigation, final ViewNames initial) {
        final List<String> loaded = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        for (final ViewNames view : ViewNames.values()) {
            if (!view.isAvailable()) {
                continue;
            }
            if (navigation.show(view).filter(view::equals).isPresent()) {
                loaded.add(view.name());
            } else {
                log.debug("resolve-all: view {} did not become current", view);
                failures.add("view " + view.name() + " did not load");
            }
        }
        navigation.show(initial);
        return new Outcome(loaded, List.of(), failures);
    }

    static Outcome resolvePorts(final Injector injector) {
        final List<String> resolved = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        for (final Key<?> key : portKeys(injector)) {
            final String name = key.getTypeLiteral().getRawType().getSimpleName();
            try {
                injector.getInstance(key);
                resolved.add(name);
            } catch (RuntimeException | LinkageError e) {
                log.debug("resolve-all: port {} failed", key, e);
                failures.add("port " + key + " failed: " + e.getClass().getSimpleName());
            }
        }
        return new Outcome(List.of(), resolved, failures);
    }

    private static List<Key<?>> portKeys(final Injector injector) {
        return injector.getBindings().values().stream()
                .map(Binding::getKey)
                .filter(key ->
                        key.getTypeLiteral().getRawType().getPackageName().startsWith(API_PACKAGE))
                .<Key<?>>map(key -> key)
                .toList();
    }
}
