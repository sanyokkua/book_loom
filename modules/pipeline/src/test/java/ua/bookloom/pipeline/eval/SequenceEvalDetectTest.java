package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;

/** The {@code detect} mode of the sequence eval answers the Start question the way the application would. */
class SequenceEvalDetectTest {

    private static final SequenceFixture FIXTURE = SequenceFixture.load();

    @TempDir
    private Path workDir;

    private Narrator storedNarrator(final SequenceNarratorMode mode, final Gender answer) throws IOException {
        final Path book =
                Files.writeString(workDir.resolve("seq.md"), SequenceFixture.bookText(), StandardCharsets.UTF_8);
        final Prepared prepared = SequenceJobs.importBook(
                book, SequenceJobs.brief(FIXTURE.source(), FIXTURE.target(), QualityDial.FAST, mode.narrator()));

        new SequenceEval(FIXTURE, QualityDial.FAST, null, mode, answer).applyDetectedNarrator(prepared);

        final Project stored = Objects.requireNonNull(
                        prepared.stores().projects().find(prepared.projectId()).data())
                .orElseThrow();
        return stored.brief().narrator();
    }

    @Test
    void applyDetectedNarrator_detectModeAnsweredFemale_storesAFirstPersonFemaleNarrator() throws IOException {
        assertThat(storedNarrator(SequenceNarratorMode.DETECT, Gender.FEMALE))
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
    }

    @Test
    void applyDetectedNarrator_detectModeDefaultAnswer_storesAFirstPersonMaleNarrator() throws IOException {
        assertThat(storedNarrator(SequenceNarratorMode.DETECT, Gender.MALE))
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));
    }

    @Test
    void applyDetectedNarrator_unsetMode_leavesTheBriefAlone() throws IOException {
        assertThat(storedNarrator(SequenceNarratorMode.UNSET, Gender.MALE)).isEqualTo(Narrator.unspecified());
    }
}
