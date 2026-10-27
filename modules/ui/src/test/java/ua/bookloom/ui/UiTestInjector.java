package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.EnvironmentVariables;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.persistence.StorageInfo;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.settings.GenerationOverrides;
import ua.bookloom.api.settings.ReviewModeChoice;
import ua.bookloom.ui.dialog.ContinuePrompt;
import ua.bookloom.ui.dialog.FindBookPrompt;
import ua.bookloom.ui.dialog.ModelPickPrompt;
import ua.bookloom.ui.dialog.OpenAnotherBookPrompt;
import ua.bookloom.ui.dialog.RenamePrompt;
import ua.bookloom.ui.dialog.SamePairPrompt;
import ua.bookloom.ui.dialog.SwitchProviderPrompt;
import ua.bookloom.ui.dialog.TextClipboard;
import ua.bookloom.ui.dialog.TranslateIntoPrompt;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.ProjectActions;
import ua.bookloom.ui.state.RowOpener;
import ua.bookloom.ui.theme.ThemeBlock;

/**
 * Builds the real {@link UiModule} graph with only the operating system's language, colour scheme, restart and the build
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

    /** The moment every graph's clock stands at, so "today" and "yesterday" on a project row never depend on the day. */
    public static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    /** The log folder a graph names unless a test sets one; nothing is written there by the window itself. */
    public static final Path TEST_LOG_DIR = Path.of(System.getProperty("java.io.tmpdir"), "bookloom-ui-test-logs");

    /** A fresh injector over {@link UiModule} in this language, every port an idle test fake. */
    public static Injector create(final Locale locale) {
        return builder(locale).build();
    }

    /** Starts a graph whose collaborators a test replaces one by one; each defaults to an idle fake. */
    public static Builder builder(final Locale locale) {
        return new Builder(Objects.requireNonNull(locale, "locale"));
    }

    /** Collects the collaborators of one test graph; {@link #build()} makes a fresh injector from them. */
    public static final class Builder {

        private final Locale locale;
        private final LaunchPorts launch = new LaunchPorts();
        private ProviderConfigs configs = new FakeProviderConfigs();
        private ProviderVerifier verifier = ScriptedProviderVerifier.idle();
        private ModelCatalog catalog = ScriptedModelCatalog.idle();
        private ProjectService projects = new ScriptedProjectService();
        private LanguageSupport languages = tag -> true;
        private ExportService exports = new ScriptedExportService();
        private ChatModelFactory models = ScriptedChatModelFactory.ok();
        private TranslationEngine engine = ScriptedTranslationEngine.idle();
        private ScriptedGlossaryService glossary = new ScriptedGlossaryService();
        private ScriptedLexiconService lexicon = new ScriptedLexiconService();
        private ScriptedNamesPass namesPass = new ScriptedNamesPass();
        private ScriptedReviewDesk desk = new ScriptedReviewDesk();
        private Supplier<ReviewMode> reviewMode = () -> ReviewMode.UNATTENDED;
        private final ReplacementQuestions replacements = new ReplacementQuestions();
        private @Nullable ExecutorService backgroundExecutor;
        private boolean gated;
        private SettingsRepository settings = new MemorySettings();
        private FileRevealer revealer = new RecordingFileRevealer();
        private @Nullable ProjectActions projectActions;
        private Set<String> environment = Set.of();
        private ModelSettingsRepository modelSettings = new ScriptedModelSettings();
        private ModelCapabilities capabilities = UiTestBindings.UNKNOWN_CONTEXT;

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

        /** What the graph's {@link LanguageSupport} is; by default every language has tested rules. */
        public Builder languages(final LanguageSupport value) {
            languages = Objects.requireNonNull(value, "languages");
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

        /** What the graph's {@link LexiconService} is, so the test can fill and read the recurring terms. */
        public Builder lexicon(final ScriptedLexiconService value) {
            lexicon = Objects.requireNonNull(value, "lexicon");
            return this;
        }

        /** What the graph's {@link NamesPass} is, so the test can script the estimate and the pass it is answered. */
        public Builder namesPass(final ScriptedNamesPass value) {
            namesPass = Objects.requireNonNull(value, "namesPass");
            return this;
        }

        /** What the graph's {@link ReviewDesk} is, so the test can script the counts and the queue it is answered. */
        public Builder reviewDesk(final ScriptedReviewDesk value) {
            desk = Objects.requireNonNull(value, "desk");
            return this;
        }

        /** The review mode the graph was launched with. */
        public Builder reviewMode(final ReviewMode value) {
            Objects.requireNonNull(value, "reviewMode");
            reviewMode = () -> value;
            return this;
        }

        /** Asks the mode again each time the graph reads it, as the window does at each run start. */
        public Builder reviewModeProvider(final Supplier<ReviewMode> value) {
            reviewMode = Objects.requireNonNull(value, "reviewMode");
            return this;
        }

        /** What the graph's {@link StorageInfo} is. */
        public Builder storage(final StorageInfo value) {
            launch.storage(Objects.requireNonNull(value, "storage"));
            return this;
        }

        /** What the graph's {@link ReviewModeChoice} is. */
        public Builder reviewModeChoice(final ReviewModeChoice value) {
            launch.reviewModeChoice(Objects.requireNonNull(value, "reviewModeChoice"));
            return this;
        }

        /** What the graph's {@link SettingsRepository} is, so the test can read what the window stored. */
        public Builder settings(final SettingsRepository value) {
            settings = Objects.requireNonNull(value, "settings");
            return this;
        }

        /** What the graph's {@link FileRevealer} is, so the test can read what the window asked to show. */
        public Builder revealer(final FileRevealer value) {
            revealer = Objects.requireNonNull(value, "revealer");
            return this;
        }

        /** Replaces the Projects screen's rename, remove, translate-into and find-book actions with this recording one. */
        public Builder projectActions(final ProjectActions value) {
            projectActions = Objects.requireNonNull(value, "projectActions");
            return this;
        }

        /** The measured speeds the graph's estimate reads. */
        public Builder modelSettings(final ModelSettingsRepository value) {
            modelSettings = Objects.requireNonNull(value, "modelSettings");
            return this;
        }

        /** The environment variables that count as set, so a credential's status can be shown without the real environment. */
        public Builder environment(final Set<String> value) {
            environment = Set.copyOf(Objects.requireNonNull(value, "environment"));
            return this;
        }

        /** Where the graph says its logs are, and whether the detailed log is on. */
        public Builder diagnosticLog(final DiagnosticLog value) {
            launch.diagnosticLog(Objects.requireNonNull(value, "diagnosticLog"));
            return this;
        }

        /** Replaces the real "Open another book?" card with this question, so the test can read what was asked. */
        public Builder prompt(final OpenAnotherBookPrompt value) {
            replacements.prompt = Objects.requireNonNull(value, "prompt");
            return this;
        }

        /** Replaces the real continue questions with this fake, so the test can read what was asked and answer it. */
        public Builder continuePrompt(final ContinuePrompt value) {
            replacements.continuePrompt = Objects.requireNonNull(value, "continuePrompt");
            return this;
        }

        /** Replaces the real "which model" question of the first-launch look with this fake. */
        public Builder modelPickPrompt(final ModelPickPrompt value) {
            replacements.modelPickPrompt = Objects.requireNonNull(value, "modelPickPrompt");
            return this;
        }

        public Builder switchProviderPrompt(final SwitchProviderPrompt value) {
            replacements.switchProviderPrompt = Objects.requireNonNull(value, "switchProviderPrompt");
            return this;
        }

        public Builder clipboard(final TextClipboard value) {
            replacements.clipboard = Objects.requireNonNull(value, "clipboard");
            return this;
        }

        /** Replaces the real Rename card with this fake. */
        public Builder renamePrompt(final RenamePrompt value) {
            replacements.renamePrompt = Objects.requireNonNull(value, "renamePrompt");
            return this;
        }

        /** Replaces the real Same language pair card with this fake. */
        public Builder samePairPrompt(final SamePairPrompt value) {
            replacements.samePairPrompt = Objects.requireNonNull(value, "samePairPrompt");
            return this;
        }

        /** Replaces the real Translate into another language card with this fake. */
        public Builder translateIntoPrompt(final TranslateIntoPrompt value) {
            replacements.translateIntoPrompt = Objects.requireNonNull(value, "translateIntoPrompt");
            return this;
        }

        /** Replaces the real Find the book card with this fake. */
        public Builder findBookPrompt(final FindBookPrompt value) {
            replacements.findBookPrompt = Objects.requireNonNull(value, "findBookPrompt");
            return this;
        }

        /** Replaces what opens a row's project after a dialog (the real one navigates) with this fake. */
        public Builder rowOpener(final RowOpener value) {
            replacements.rowOpener = Objects.requireNonNull(value, "rowOpener");
            return this;
        }

        /** Replaces the modal error presenter with this fake, so the test can read what was shown. */
        public Builder errorPresenter(final ErrorPresenter value) {
            replacements.errorPresenter = Objects.requireNonNull(value, "errorPresenter");
            return this;
        }

        /** Replaces the daemon pool behind {@code @BackgroundExecutor}, e.g. with a calling-thread executor. */
        public Builder backgroundExecutor(final ExecutorService value) {
            backgroundExecutor = Objects.requireNonNull(value, "backgroundExecutor");
            return this;
        }

        /** What the graph's {@link ModelCapabilities} is, so the test can script what the program says about a model. */
        public Builder capabilities(final ModelCapabilities value) {
            capabilities = Objects.requireNonNull(value, "capabilities");
            return this;
        }

        /** What the graph's {@link AppRestart} is, so the test can read whether and when a restart was asked. */
        public Builder restart(final AppRestart value) {
            launch.restart(Objects.requireNonNull(value, "restart"));
            return this;
        }

        /** What the graph says overrides a Generation setting for this launch. */
        public Builder overrides(final GenerationOverrides value) {
            launch.overrides(Objects.requireNonNull(value, "overrides"));
            return this;
        }

        /** The operating system's language; by default the display language, so no restart is ever needed. */
        public Builder osLocale(final Locale value) {
            launch.osLocale(Objects.requireNonNull(value, "osLocale"));
            return this;
        }

        /** What the operating system reports as its colour scheme; by default light. */
        public Builder colorScheme(final Optional<ThemeBlock> value) {
            launch.colorScheme(Objects.requireNonNull(value, "colorScheme"));
            return this;
        }

        /** Keeps the real step gating; by default every step is open so a screen test needs no book. */
        public Builder gated() {
            gated = true;
            return this;
        }

        /** Makes the graph: a fresh injector that shares no singleton with another call. */
        public Injector build() {
            final Module open = gated ? Modules.EMPTY_MODULE : new OpenStepsModule();
            return Guice.createInjector(Modules.override(new UiModule())
                    .with(
                            new ReviewPortsModule(glossary, lexicon, namesPass, desk, reviewMode),
                            new ScriptedPorts(),
                            replacements.module(),
                            open));
        }

        private final class ScriptedPorts extends AbstractModule {

            @Override
            protected void configure() {
                install(launch.module(locale));
                UiTestBindings.bindExecutors(binder(), backgroundExecutor);
                UiTestBindings.bindStores(binder(), configs, settings, modelSettings, NOW);
                bind(EnvironmentVariables.class).toInstance(name -> environment.contains(name));
                bind(ProviderVerifier.class).toInstance(verifier);
                bind(ModelCatalog.class).toInstance(catalog);
                bind(ModelCapabilities.class).toInstance(capabilities);
                bind(ChatModelFactory.class).toInstance(models);
                bind(TranslationEngine.class).toInstance(engine);
                bind(ProjectService.class).toInstance(projects);
                bind(LanguageSupport.class).toInstance(languages);
                bind(ExportService.class).toInstance(exports);
                install(new ExportExtrasModule(revealer));
                if (projectActions != null) {
                    bind(ProjectActions.class).toInstance(projectActions);
                }
            }
        }
    }
}
