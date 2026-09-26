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
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
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
     * threads, and whose provider registry, verifier, model catalogue and document port are the default test fakes (the
     * two built-in presets, a verifier nobody is expected to ask, a catalogue that lists no models, a port that
     * answers every book with an error, a model factory that hands out a model and an engine that refuses every job).
     *
     * @param locale the language every message renders in
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(final Locale locale) {
        return create(locale, new FakeProviderConfigs(), ScriptedProviderVerifier.idle(), ScriptedModelCatalog.idle());
    }

    /**
     * As {@link #create(Locale)} with a scripted document port, for a test that opens books.
     *
     * @param locale the language every message renders in
     * @param documents what the graph's {@link DocumentPort} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(final Locale locale, final DocumentPort documents) {
        return create(
                locale,
                new FakeProviderConfigs(),
                ScriptedProviderVerifier.idle(),
                ScriptedModelCatalog.idle(),
                documents);
    }

    /**
     * As {@link #create(Locale)} with a scripted verifier, for a test that drives or observes provider checks.
     *
     * @param locale the language every message renders in
     * @param verifier what the graph's {@link ProviderVerifier} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(final Locale locale, final ProviderVerifier verifier) {
        return create(locale, new FakeProviderConfigs(), verifier, ScriptedModelCatalog.idle());
    }

    /**
     * As {@link #create(Locale)} with a scripted verifier and a scripted model catalogue, for a test that drives or
     * observes model listing.
     *
     * @param locale the language every message renders in
     * @param verifier what the graph's {@link ProviderVerifier} is
     * @param catalog what the graph's {@link ModelCatalog} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(final Locale locale, final ProviderVerifier verifier, final ModelCatalog catalog) {
        return create(locale, new FakeProviderConfigs(), verifier, catalog);
    }

    /**
     * As {@link #create(Locale)} with a scripted provider registry and verifier.
     *
     * @param locale the language every message renders in
     * @param configs what the graph's {@link ProviderConfigs} is
     * @param verifier what the graph's {@link ProviderVerifier} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(final Locale locale, final ProviderConfigs configs, final ProviderVerifier verifier) {
        return create(locale, configs, verifier, ScriptedModelCatalog.idle());
    }

    /**
     * As {@link #create(Locale)} with a scripted provider registry, verifier and model catalogue.
     *
     * @param locale the language every message renders in
     * @param configs what the graph's {@link ProviderConfigs} is
     * @param verifier what the graph's {@link ProviderVerifier} is
     * @param catalog what the graph's {@link ModelCatalog} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(
            final Locale locale,
            final ProviderConfigs configs,
            final ProviderVerifier verifier,
            final ModelCatalog catalog) {
        return create(locale, configs, verifier, catalog, ScriptedDocumentPort.idle());
    }

    /**
     * As {@link #create(Locale)} with every collaborator scripted.
     *
     * @param locale the language every message renders in
     * @param configs what the graph's {@link ProviderConfigs} is
     * @param verifier what the graph's {@link ProviderVerifier} is
     * @param catalog what the graph's {@link ModelCatalog} is
     * @param documents what the graph's {@link DocumentPort} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(
            final Locale locale,
            final ProviderConfigs configs,
            final ProviderVerifier verifier,
            final ModelCatalog catalog,
            final DocumentPort documents) {
        return create(
                locale,
                configs,
                verifier,
                catalog,
                documents,
                ScriptedChatModelFactory.ok(),
                ScriptedTranslationEngine.idle());
    }

    /**
     * As {@link #create(Locale, DocumentPort)} with a scripted model factory and translation engine, for a test that
     * starts runs.
     *
     * @param locale the language every message renders in
     * @param documents what the graph's {@link DocumentPort} is
     * @param models what the graph's {@link ChatModelFactory} is
     * @param engine what the graph's {@link TranslationEngine} is
     * @return a fresh injector; never shares singletons with another call
     */
    public static Injector create(
            final Locale locale,
            final DocumentPort documents,
            final ChatModelFactory models,
            final TranslationEngine engine) {
        return create(
                locale,
                new FakeProviderConfigs(),
                ScriptedProviderVerifier.idle(),
                ScriptedModelCatalog.idle(),
                documents,
                models,
                engine);
    }

    private static Injector create(
            final Locale locale,
            final ProviderConfigs configs,
            final ProviderVerifier verifier,
            final ModelCatalog catalog,
            final DocumentPort documents,
            final ChatModelFactory models,
            final TranslationEngine engine) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(engine, "engine");
        final LocaleProvider fixed = () -> locale;
        final ColorSchemeProvider light = () -> Optional.of(ThemeBlock.LIGHT);
        final FileRevealer revealer = new RecordingFileRevealer();
        return Guice.createInjector(Modules.override(new UiModule()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(LocaleProvider.class).toInstance(fixed);
                bind(ColorSchemeProvider.class).toInstance(light);
                bind(String.class).annotatedWith(BuildVersion.class).toInstance(DEV_VERSION);
                bind(ExecutorService.class)
                        .annotatedWith(BackgroundExecutor.class)
                        .toInstance(daemonExecutor());
                bind(ProviderConfigs.class).toInstance(configs);
                bind(ProviderVerifier.class).toInstance(verifier);
                bind(ModelCatalog.class).toInstance(catalog);
                bind(DocumentPort.class).toInstance(documents);
                bind(ChatModelFactory.class).toInstance(models);
                bind(TranslationEngine.class).toInstance(engine);
                bind(FileRevealer.class).toInstance(revealer);
            }
        }));
    }

    private static ExecutorService daemonExecutor() {
        return Executors.newFixedThreadPool(BACKGROUND_THREADS, task -> {
            final Thread thread = new Thread(task, "ui-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }
}
