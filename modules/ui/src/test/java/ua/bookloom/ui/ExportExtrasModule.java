package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.ui.state.CsvFileChooser;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.FileRevealer;

/** The export screen's collaborators beside the export service: the setup assistant, the revealer, the choosers and the project store that keeps the last export. */
final class ExportExtrasModule extends AbstractModule {

    private final FileRevealer revealer;

    ExportExtrasModule(final FileRevealer revealer) {
        this.revealer = revealer;
    }

    @Override
    protected void configure() {
        bind(SetupAssistant.class).toInstance(new ScriptedSetupAssistant());
        bind(FileRevealer.class).toInstance(revealer);
        bind(DestinationChooser.class).toInstance(new RecordingDestinationChooser());
        bind(CsvFileChooser.class).toInstance(new RecordingCsvChooser());
        bind(ProjectRepository.class).toInstance(new RecordingProjectRepository());
    }
}
