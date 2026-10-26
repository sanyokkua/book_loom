package ua.bookloom.app;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.Objects;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.pipeline.PipelineModule;

/** The service graph shared by the desktop application and command-line launchers. */
public final class CoreModules extends AbstractModule {

    private final StartupContext startup;
    private final Module persistence;
    private final SystemEnvironmentVariables environment;

    /**
     * Creates the shared graph over the launcher's already-resolved startup state.
     *
     * @param startup what the launcher resolved
     * @param persistence the storage bindings: the SQLite variant for the window, the in-memory one for the command
     *     line and the tests
     */
    public CoreModules(StartupContext startup, Module persistence) {
        this(startup, persistence, new SystemEnvironmentVariables());
    }

    // One lookup serves the request headers and the Providers form, so "set" in the form means the request has a key.
    CoreModules(StartupContext startup, Module persistence, SystemEnvironmentVariables environment) {
        this.startup = Objects.requireNonNull(startup, "startup");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @Override
    protected void configure() {
        install(new AppModule(startup, environment));
        install(new DocumentModule());
        install(new LlmModule(environment::value));
        install(persistence);
        install(new PipelineModule());
    }
}
