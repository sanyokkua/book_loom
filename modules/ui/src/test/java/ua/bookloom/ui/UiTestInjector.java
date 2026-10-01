package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
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
import ua.bookloom.ui.dialog.ReplaceRunPrompt;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.state.DestinationChooser;
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

    /** The log folder a graph names unless a test sets one; nothing is written there by the window itself. */
    public static final Path TEST_LOG_DIR = Path.of(System.getProperty("java.io.tmpdir"), "bookloom-ui-test-logs");

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
        private ScriptedGlossaryService glossary = new ScriptedGlossaryService();
        private ScriptedReviewDesk desk = new ScriptedReviewDesk();
        private ReviewMode reviewMode = ReviewMode.UNATTENDED;
        private @Nullable ReplaceRunPrompt prompt;
        private DiagnosticLog diagnosticLog = new DiagnosticLog(TEST_LOG_DIR, false);

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

        /** What the graph's {@link GlossaryService} is, so the test can script what the names screen is answered. */
        public Builder glossary(final ScriptedGlossaryService value) {
            glossary = Objects.requireNonNull(value, "glossary");
            return this;
        }

        /** What the graph's {@link ReviewDesk} is, so the test can script the counts and the queue it is answered. */
        public Builder reviewDesk(final ScriptedReviewDesk value) {
            desk = Objects.requireNonNull(value, "desk");
            return this;
        }

        /** The review mode the graph was launched with. */
        public Builder reviewMode(final ReviewMode value) {
            reviewMode = Objects.requireNonNull(value, "reviewMode");
            return this;
        }

        /** Where the graph says its logs are, and whether the detailed log is on. */
        public Builder diagnosticLog(final DiagnosticLog value) {
            diagnosticLog = Objects.requireNonNull(value, "diagnosticLog");
            return this;
        }

        /** Replaces the real replace-run card with this question, so the test can read what was asked. */
        public Builder prompt(final ReplaceRunPrompt value) {
            prompt = Objects.requireNonNull(value, "prompt");
            return this;
        }

        /**
         * Makes the graph.
         *
         * @return a fresh injector; never shares singletons with another call
         */
        public Injector build() {
            final ReplaceRunPrompt replacement = prompt;
            return Guice.createInjector(Modules.override(new UiModule())
                    .with(new ReviewPortsModule(glossary, desk, reviewMode), new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(LocaleProvider.class).toInstance(() -> locale);
                            bind(ColorSchemeProvider.class).toInstance(() -> Optional.of(ThemeBlock.LIGHT));
                            bind(String.class).annotatedWith(BuildVersion.class).toInstance(DEV_VERSION);
                            bind(DiagnosticLog.class).toInstance(diagnosticLog);
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
                            bind(DestinationChooser.class).toInstance(new RecordingDestinationChooser());
                            if (replacement != null) {
                                bind(ReplaceRunPrompt.class).toInstance(replacement);
                            }
                        }
                    }));
        }
    }

    /** The review-side ports and the launch-time review mode, which no test of the window scripts yet. */
    private static final class ReviewPortsModule extends AbstractModule {

        private final GlossaryService glossary;
        private final ReviewDesk desk;
        private final ReviewMode reviewMode;

        ReviewPortsModule(final GlossaryService glossary, final ReviewDesk desk, final ReviewMode reviewMode) {
            this.glossary = glossary;
            this.desk = desk;
            this.reviewMode = reviewMode;
        }

        @Override
        protected void configure() {
            bind(GlossaryService.class).toInstance(glossary);
            bind(ReviewDesk.class).toInstance(desk);
            bind(ReviewMode.class).toInstance(reviewMode);
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
