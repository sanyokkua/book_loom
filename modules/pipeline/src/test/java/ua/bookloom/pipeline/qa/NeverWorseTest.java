package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.TermType;

/**
 * {@link NeverWorse}: one test per veto, and the score orderings that decide a candidate no veto refuses. Both texts
 * are evaluated by the real {@link QaEvaluator}; no weight's value is asserted.
 */
class NeverWorseTest {

    private static final String EN = "en";
    private static final String RU = "ru";
    private static final String UK = "uk";
    private static final String RESIDUE = "function-word-residue";
    private static final String RU_SOURCE = "Они сказали, что ключ в кармане.";
    private static final String UK_WITH_CHTO = "Вони сказали, что ключ у кишені.";
    private static final String UK_CLEAN = "Вони сказали, що ключ у кишені.";
    private static final String LONG_SOURCE =
            "The old man walked slowly along the quiet river bank, looking at the dark"
                    + " water and thinking about his long life and the past years.";
    private static final String LONG_CURRENT = "Старий чоловік повільно йшов уздовж тихого берега річки, дивлячись на"
            + " темну воду і думаючи про своє довге життя та минулі роки.";

    private static Candidate evaluated(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final Narrator narrator,
            final List<CheckResult> gates) {
        final SoftCheckInput input = new SoftCheckInput(
                source,
                target,
                target,
                sourceLanguage,
                UK,
                ForeignPassagePolicy.TRANSLATE,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of(),
                narrator,
                List.of(),
                List.of(),
                UnitRole.BODY,
                List.of());
        return new Candidate(target, QaEvaluator.evaluate(gates, input), List.of());
    }

    private static Candidate en(final String source, final String target) {
        return evaluated(source, target, EN, Narrator.unspecified(), List.of());
    }

    private static Candidate ru(final String target) {
        return evaluated(RU_SOURCE, target, RU, Narrator.unspecified(), List.of());
    }

    private static WindowFacts facts(final String source, final String sourceLanguage) {
        return WindowFacts.of(source, sourceLanguage, UK, List.of(), null, false);
    }

    private static WindowFacts marked(final String source, final String sourceLanguage, final QaFinding trigger) {
        return WindowFacts.of(source, sourceLanguage, UK, List.of(), trigger, false);
    }

    private static WindowFacts withName(final String source) {
        final GlossaryEntry velemir =
                GlossaryEntry.of("e1", "p1", "Velemir", "Велемир", TermType.CHARACTER, Gender.MALE, false);
        return WindowFacts.of(source, EN, UK, List.of(velemir), null, false);
    }

    private static QaFinding findingOf(final Candidate text, final String raisedBy) {
        return text.qa().findings().stream()
                .filter(finding -> finding.raisedBy().equals(raisedBy))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void judge_addsRussianWordChto_rejectsResidue() {
        final Verdict verdict = NeverWorse.judge(ru(UK_CLEAN), ru(UK_WITH_CHTO), facts(RU_SOURCE, RU));

        assertThat(verdict).isEqualTo(new Verdict.Reject(RESIDUE));
    }

    @Test
    void judge_dropsNegation_rejects() {
        final String source = "He did not go and did not call.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він не пішов і не подзвонив."), en(source, "Він не пішов і подзвонив."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("negation"));
    }

    @Test
    void judge_pinnedNameForeignForm_rejects() {
        final String source = "Velemir smiled at her.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Велемир усміхнувся їй."), en(source, "Велемер усміхнувся їй."), withName(source));

        assertThat(verdict).isEqualTo(new Verdict.Reject("name-form"));
    }

    @Test
    void judge_pinnedNameListedForm_keeps() {
        final String source = "She smiled at Velemir.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Вона усміхнулася Велемиру."),
                en(source, "Вона усміхнулася Велемирові."),
                WindowFacts.of(
                        source,
                        EN,
                        UK,
                        List.of(GlossaryEntry.of(
                                "e1", "p1", "Velemir", "Велемир", TermType.CHARACTER, Gender.MALE, false)),
                        null,
                        true));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_dropsMapName_rejects() {
        final String source = "Velemir smiled at her.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Велемир усміхнувся їй."), en(source, "Він усміхнувся їй."), withName(source));

        assertThat(verdict).isEqualTo(new Verdict.Reject("name-dropped"));
    }

    @Test
    void judge_losesSentence_rejects() {
        final String source = "He came. He sat. He slept.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він прийшов. Він сів. Він заснув."),
                en(source, "Він прийшов. Він сів і заснув."),
                facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("sentences"));
    }

    @Test
    void judge_addsStressMark_rejects() {
        final String source = "Mom came home late.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Мама прийшла додому пізно."), en(source, "Ма́ма прийшла додому пізно."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("stress-mark"));
    }

    @Test
    void judge_dropsPlaceholder_rejects() {
        final String source = "He opened the ⟦g0⟧old⟦g1⟧ door.";
        final Candidate current = en(source, "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        final Candidate candidate = evaluated(
                source,
                "Він відчинив старі двері.",
                EN,
                Narrator.unspecified(),
                List.of(CheckResult.hardGateFailed(CheckName.PLACEHOLDER, "placeholder multiset mismatch")));

        assertThat(NeverWorse.judge(current, candidate, facts(source, EN)))
                .isEqualTo(new Verdict.Reject("placeholder"));
    }

    @Test
    void judge_newBlockingFinding_rejects() {
        final String source = "He climbed onto the mare.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він заліз на кобилу."), en(source, "Він заліз на кобылу."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("new-block:alphabet"));
    }

    @Test
    void judge_addsLatinRun_rejects() {
        final String source = "He said that it was very good, and he went home slowly.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він сказав, що це дуже добре, і повільно пішов додому, мовчки."),
                en(source, "Він сказав, що це дуже добре, і повільно пішов додому, quick brown."),
                facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("latin-run"));
    }

    @Test
    void judge_losesNumber_rejects() {
        final String source = "He paid 40 coins.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він заплатив 40 монет."), en(source, "Він заплатив монети."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("number-lost"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "He said «go».|Він сказав «іди».|Він сказав іди.|quotes",
                "— Go, he said.|— Іди, сказав він.|Іди, сказав він.|dashes",
                "Go, he said.|Іди, сказав він.|Іди сказав він.|clauses"
            })
    void judge_losesQuoteDashClause_rejects(
            final String source, final String current, final String candidate, final String reason) {
        assertThat(NeverWorse.judge(en(source, current), en(source, candidate), facts(source, EN)))
                .isEqualTo(new Verdict.Reject(reason));
    }

    @Test
    void judge_raisesNumberWhileClearingResidue_rejects() {
        final String source = "Они сказали, что заплатили 40 монет.";
        final Candidate current =
                evaluated(source, "Вони сказали, что заплатили 40 монет.", RU, Narrator.unspecified(), List.of());
        final Candidate candidate =
                evaluated(source, "Вони сказали, що заплатили монети.", RU, Narrator.unspecified(), List.of());

        final Verdict verdict = NeverWorse.judge(current, candidate, marked(source, RU, findingOf(current, RESIDUE)));

        assertThat(verdict).isEqualTo(new Verdict.Reject("number-lost"));
    }

    @Test
    void judge_raisesTerminalMarkCount_rejects() {
        final String source = "Did you come?";

        final Verdict verdict =
                NeverWorse.judge(en(source, "Ти прийшов?"), en(source, "Ти прийшов."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("count:terminal-mark"));
    }

    @Test
    void judge_lowersOneCountRaisesNone_keeps() {
        final Candidate current = ru(UK_WITH_CHTO);

        final Verdict verdict =
                NeverWorse.judge(current, ru(UK_CLEAN), marked(RU_SOURCE, RU, findingOf(current, RESIDUE)));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_clearsTriggerWithEqualScore_keeps() {
        final Candidate current = ru(UK_WITH_CHTO);
        final Candidate candidate = ru("Вони сказали, що що ключ у кишені.");

        final Verdict verdict =
                NeverWorse.judge(current, candidate, marked(RU_SOURCE, RU, findingOf(current, RESIDUE)));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_noChangeInScoreTriggerLeft_rejects() {
        final Candidate current = ru(UK_WITH_CHTO);
        final Candidate candidate = ru("Вони казали, что ключ у кишені.");

        final Verdict verdict =
                NeverWorse.judge(current, candidate, marked(RU_SOURCE, RU, findingOf(current, RESIDUE)));

        assertThat(verdict).isEqualTo(new Verdict.Reject("no-gain"));
    }

    @Test
    void judge_swapsBlockForRepair_keeps() {
        final String source = "I climbed onto the mare.";
        final Narrator male = new Narrator(NarratorPerson.FIRST, Gender.MALE);
        final Candidate current = evaluated(source, "Я заліз на кобылу.", EN, male, List.of());
        final Candidate candidate = evaluated(source, "Я залізла на кобилу.", EN, male, List.of());

        assertThat(findingOf(candidate, "gender")).isNotNull();
        assertThat(NeverWorse.judge(current, candidate, facts(source, EN))).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_unmarkedMaxSmallRewriteEqualScore_keeps() {
        final String rewrite = LONG_CURRENT.replace("повільно", "неквапливо").replace("темну", "чорну");

        final Verdict verdict = NeverWorse.judge(
                en(LONG_SOURCE, LONG_CURRENT),
                en(LONG_SOURCE, rewrite),
                WindowFacts.of(LONG_SOURCE, EN, UK, List.of(), null, true));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_unmarkedMaxBigRewrite_rejects() {
        final String rewrite = "Літній дід неквапливо мандрував понад мовчазним узбережжям потоку, споглядаючи"
                + " чорну хвилю й згадуючи власне тривале існування та давні літа.";

        final Verdict verdict = NeverWorse.judge(
                en(LONG_SOURCE, LONG_CURRENT),
                en(LONG_SOURCE, rewrite),
                WindowFacts.of(LONG_SOURCE, EN, UK, List.of(), null, true));

        assertThat(verdict).isEqualTo(new Verdict.Reject("no-gain"));
    }

    @Test
    void judge_markedParagraphRewriteCapDoesNotApply() {
        final String rewrite = LONG_CURRENT.replace("повільно", "неквапливо").replace("темну", "чорну");

        final Verdict verdict =
                NeverWorse.judge(en(LONG_SOURCE, LONG_CURRENT), en(LONG_SOURCE, rewrite), facts(LONG_SOURCE, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("no-gain"));
    }

    @Test
    void judge_higherScore_rejects() {
        final String source = "He climbed onto the mare.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він заліз на кобилу."), en(source, "Він заліз на на кобилу."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("score"));
    }

    @Test
    void isWorseByBlockers_newBlocker_isWorse() {
        final String source = "He climbed onto the mare.";

        assertThat(NeverWorse.isWorseByBlockers(
                        en(source, "Він заліз на кобылу.").qa(),
                        en(source, "Він заліз на кобилу.").qa()))
                .isTrue();
    }

    @Test
    void isWorseByBlockers_sameBlockers_isNotWorse() {
        final String source = "He climbed onto the mare.";

        assertThat(NeverWorse.isWorseByBlockers(
                        en(source, "Він заліз на кобылу.").qa(),
                        en(source, "Він сів на кобылу.").qa()))
                .isFalse();
    }

    @Test
    void isWorseByBlockers_failedHardGateOutweighsBlockers_isWorse() {
        final String source = "He climbed onto the ⟦g0⟧mare⟦g1⟧.";
        final QaResult gateFailed = evaluated(
                        source,
                        "Він заліз на кобилу.",
                        EN,
                        Narrator.unspecified(),
                        List.of(CheckResult.hardGateFailed(CheckName.PLACEHOLDER, "placeholder multiset mismatch")))
                .qa();

        assertThat(NeverWorse.isWorseByBlockers(
                        gateFailed, en(source, "Він заліз на ⟦g0⟧кобилу⟦g1⟧.").qa()))
                .isTrue();
    }
}
