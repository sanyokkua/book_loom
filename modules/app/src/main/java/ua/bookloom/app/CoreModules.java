package ua.bookloom.app;

import com.google.inject.AbstractModule;
import java.util.Objects;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;

/** The service graph shared by the desktop application and command-line launchers. */
public final class CoreModules extends AbstractModule {

    private final StartupContext startup;

    /** Creates the shared graph over the launcher's already-resolved startup state. */
    public CoreModules(StartupContext startup) {
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    @Override
    protected void configure() {
        install(new AppModule(startup));
        install(new DocumentModule());
        install(new LlmModule());
        install(new PersistenceModule());
        install(new PipelineModule());
    }
}
