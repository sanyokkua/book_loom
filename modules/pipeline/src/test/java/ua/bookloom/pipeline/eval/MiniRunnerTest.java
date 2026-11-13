package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.eval.MiniBook.Block;
import ua.bookloom.pipeline.eval.MiniBook.Kind;
import ua.bookloom.pipeline.eval.MiniBook.NarratorSpec;

class MiniRunnerTest {

    @Test
    void run_datasetPair_usesPairAndNarratorFromTheDataset() {
        final MiniBook german = new MiniBook(
                "Ein Buch",
                "Anon",
                "de",
                "de-uk",
                new NarratorSpec(NarratorPerson.FIRST, Gender.FEMALE),
                List.of(new Block("m01", Kind.P, "Ich kam an.")));

        final BookBrief brief = new MiniRunner(german, QualityDial.FAST, NamePolicy.TRANSLITERATE, null).brief();

        assertThat(brief.sourceLanguage()).isEqualTo("de");
        assertThat(brief.targetLanguage()).isEqualTo("uk");
        assertThat(brief.narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(brief.narrator().gender()).isEqualTo(Gender.FEMALE);
    }
}
