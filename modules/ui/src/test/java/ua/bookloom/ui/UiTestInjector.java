package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.theme.ColorSchemeProvider;
import ua.bookloom.ui.theme.ThemeBlock;

/**
 * Builds the real {@link UiModule} graph with only the operating system's language, colour scheme and the build
 * version replaced by fixed values, so a navigation or shell test exercises the production bindings instead of a
 * hand-assembled copy of them and never depends on the machine it runs on.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UiTestInjector {

    /** The version a build that carries no injected release version reports. */
    public static final String DEV_VERSION = "dev";

    // Two, so that a held provider check and a held model listing never block each other.
    private static final int BACKGROUND_THREADS = 2;

    /**
     * Creates an injector over {@link UiModule} whose display locale is fixed, whose operating system always reports
     * the light scheme, whose build version is {@value #DEV_VERSION}, whose {@link BackgroundExecutor} is two daemon
     * threads, and whose every port is the idle test fake.
     *
     * @param locale the language every message renders in
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(final Locale locale) {
        return builder(locale).build();
    }

    /**
     * Starts a graph whose collaborators a test replaces one by one; each defaults to an idle fake: the two built-in
     * provider presets, a verifier nobody is expected to ask, a catalogue that lists no models, a project service that
     * answers every import with an error, an export service nobody is expected to ask, a model factory that hands out
     * a model and an engine that refuses every job.
     *
     * @param locale the language every message renders in
     * @return the builder
     */
    public static Builder builder(final Locale locale) {
        return new Builder(Objects.requireNonNull(locale, "locale"));
    }

    /** Collects the collaborators of one test graph; {@link #build()} makes a fresh injector from them. */
    public static final class Builder {

        private final Locale locale;
        private ProviderConfigs configs = new FakeProviderConfigs();
        private ProviderVerifier verifier = ScriptedProviderVerifier.idle();
        private ModelCatalog catalog = ScriptedModelCatalog.idle();
        private ProjectService projects = new ScriptedProjectService();
        private ExportService exports = new ScriptedExportService();
        private ChatModelFactory models = ScriptedChatModelFactory.ok();
        private TranslationEngine engine = ScriptedTranslationEngine.idle();

        private Builder(final Locale locale) {
            this.locale = locale;
        }

        /** What the graph's {@link ProviderConfigs} is. */
        public Builder configs(final ProviderConfigs value) {
            configs = Objects.requireNonNull(value, "configs");
            return this;
        }

        /** What the graph's {@link ProviderVerifier} is. */
        public Builder verifier(final ProviderVerifier value) {
            verifier = Objects.requireNonNull(value, "verifier");
            return this;
        }

        /** What the graph's {@link ModelCatalog} is. */
        public Builder catalog(final ModelCatalog value) {
            catalog = Objects.requireNonNull(value, "catalog");
            return this;
        }

        /** What the graph's {@link ProjectService} is, so the test can read what the window asked of it. */
        public Builder projects(final ProjectService value) {
            projects = Objects.requireNonNull(value, "projects");
            return this;
        }

        /** What the graph's {@link ExportService} is. */
        public Builder exports(final ExportService value) {
            exports = Objects.requireNonNull(value, "exports");
            return this;
        }

        /** What the graph's {@link ChatModelFactory} is. */
        public Builder models(final ChatModelFactory value) {
            models = Objects.requireNonNull(value, "models");
            return this;
        }

        /** What the graph's {@link TranslationEngine} is. */
        public Builder engine(final TranslationEngine value) {
            engine = Objects.requireNonNull(value, "engine");
            return this;
        }

        /**
         * Makes the graph.
         *
         * @return a fresh injector; never shares singletons with another call
         */
        public Injector build() {
            return Guice.createInjector(Modules.override(new UiModule())
                    .with(new ReviewPortsModule(), new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(LocaleProvider.class).toInstance(() -> locale);
                            bind(ColorSchemeProvider.class).toInstance(() -> Optional.of(ThemeBlock.LIGHT));
                            bind(String.class).annotatedWith(BuildVersion.class).toInstance(DEV_VERSION);
                            bind(ExecutorService.class)
                                    .annotatedWith(BackgroundExecutor.class)
                                    .toInstance(daemonExecutor());
                            bind(ProviderConfigs.class).toInstance(configs);
                            bind(ProviderVerifier.class).toInstance(verifier);
                            bind(ModelCatalog.class).toInstance(catalog);
                            bind(ChatModelFactory.class).toInstance(models);
                            bind(TranslationEngine.class).toInstance(engine);
                            bind(ProjectService.class).toInstance(projects);
                            bind(ExportService.class).toInstance(exports);
                            bind(FileRevealer.class).toInstance(new RecordingFileRevealer());
                        }
                    }));
        }
    }

    /** The review-side ports and the launch-time review mode, which no test of the window scripts yet. */
    private static final class ReviewPortsModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(GlossaryService.class).toInstance(new ScriptedGlossaryService());
            bind(ReviewDesk.class).toInstance(new ScriptedReviewDesk());
            bind(ReviewMode.class).toInstance(ReviewMode.UNATTENDED);
        }
    }

    private static ExecutorService daemonExecutor() {
        return Executors.newFixedThreadPool(BACKGROUND_THREADS, task -> {
            final Thread thread = new Thread(task, "ui-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }
}
