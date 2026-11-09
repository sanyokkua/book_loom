package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.CallKind;

/** Behaviour every {@link ModelSettingsRepository} adapter must show. */
public abstract class ModelSettingsContractTest {

    protected abstract ModelSettingsRepository repository();

    private static ModelSettings sample(final String provider, final String model) {
        return new ModelSettings(
                provider,
                model,
                TemperatureChoice.VALUE,
                0.4,
                8192,
                1024,
                OutputLengthField.MAX_COMPLETION_TOKENS,
                List.of(new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "left it out")),
                Map.of(CallKind.DRAFT, new MeasuredSpeed(2.5, MeasuredFrom.TEST)),
                ProbeVerdicts.none(),
                ThinkingOverride.RECOMMENDED,
                SamplingOverride.profileDefault());
    }

    @Test
    void save_modelSettingsWithSchemaVerdictHonouredFromTest_roundTrips() {
        final ProbeVerdicts.Verdict verdict = new ProbeVerdicts.Verdict(
                ProbeVerdicts.Outcome.HONOURED, "test", java.time.Instant.parse("2026-10-29T10:00:00Z"));
        final ModelSettings base = sample("ollama", "verdicts");
        final ModelSettings withVerdict = new ModelSettings(
                base.providerId(),
                base.modelId(),
                base.temperatureChoice(),
                base.temperature(),
                base.contextWindow(),
                base.maxOutput(),
                base.outputField(),
                base.refused(),
                base.secondsPerCall(),
                new ProbeVerdicts(verdict, null, null, null),
                ThinkingOverride.RECOMMENDED,
                SamplingOverride.profileDefault());

        repository().save(withVerdict);

        final ModelSettings found =
                repository().find("ollama", "verdicts").data().orElseThrow();
        assertThat(found.probeVerdicts().answerSchema()).isEqualTo(verdict);
        assertThat(found.probeVerdicts().capField()).isNull();
        assertThat(found).isEqualTo(withVerdict);
    }

    @Test
    void save_overridesAndScopedRefusal_roundTrip() {
        final RefusedSetting schemaForDraft =
                new RefusedSetting("response_format", LocalDate.of(2026, 10, 30), "asked through the prompt", "draft");
        final ModelSettings saved = ModelSettings.defaults("office", "gpt-oss:20b")
                .withOverrides(ThinkingOverride.ALWAYS, SamplingOverride.custom(0.4, 20, 0.9, 0.05))
                .withRefused(List.of(sample("office", "gpt-oss:20b").refused().get(0), schemaForDraft));

        assertThat(repository().save(saved).isOk()).isTrue();

        assertThat(repository().find("office", "gpt-oss:20b").data()).hasValueSatisfying(found -> {
            assertThat(found).isEqualTo(saved);
            assertThat(found.thinkingOverride()).isEqualTo(ThinkingOverride.ALWAYS);
            assertThat(found.samplingOverride()).isEqualTo(SamplingOverride.custom(0.4, 20, 0.9, 0.05));
            assertThat(found.refused()).extracting(RefusedSetting::scope).containsExactly(null, "draft");
        });
    }

    @Test
    void find_nothingSaved_returnsEmpty() {
        assertThat(repository().find("ollama", "gemma").data()).isEmpty();
    }

    @Test
    void save_thenFind_returnsTheSameSettings() {
        final ModelSettings saved = sample("ollama", "gemma");

        assertThat(repository().save(saved).isOk()).isTrue();

        assertThat(repository().find("ollama", "gemma").data()).hasValue(saved);
    }

    @Test
    void save_runMeasuredSpeed_returnsTheSpeedAndItsSource() {
        final ModelSettings saved = sample("ollama", "gemma").withMeasuredSpeed(CallKind.DRAFT, 0.8, MeasuredFrom.RUN);
        repository().save(saved);

        final ModelSettings found = repository().find("ollama", "gemma").data().orElseThrow();

        assertThat(found.speedOf(CallKind.DRAFT).map(MeasuredSpeed::seconds)).hasValue(0.8);
        assertThat(found.speedOf(CallKind.DRAFT).map(MeasuredSpeed::from)).hasValue(MeasuredFrom.RUN);
    }

    @Test
    void save_noMeasuredSpeed_returnsNoSource() {
        repository().save(ModelSettings.defaults("ollama", "gemma"));

        assertThat(repository().find("ollama", "gemma").data().orElseThrow().secondsPerCall())
                .isEmpty();
    }

    @Test
    void save_twice_keepsTheLast() {
        repository().save(sample("ollama", "gemma"));
        final ModelSettings cleared = ModelSettings.defaults("ollama", "gemma");
        repository().save(cleared);

        assertThat(repository().find("ollama", "gemma").data()).hasValue(cleared);
    }

    @Test
    void find_otherModelOrProvider_isKeptApart() {
        repository().save(sample("ollama", "gemma"));

        assertThat(repository().find("ollama", "other").data()).isEmpty();
        assertThat(repository().find("lmstudio", "gemma").data()).isEmpty();
    }

    @Test
    void all_listsEverySavedModel() {
        repository().save(sample("ollama", "gemma"));
        repository().save(sample("lmstudio", "qwen"));

        assertThat(repository().all().data())
                .extracting(ModelSettings::modelId)
                .containsExactlyInAnyOrder("gemma", "qwen");
    }

    @Test
    void all_nothingSaved_isEmpty() {
        assertThat(repository().all().data()).isEmpty();
    }

    @Test
    void delete_savedModel_removesItAndTheOthersStay() {
        repository().save(sample("ollama", "gemma"));
        repository().save(sample("ollama", "other"));

        assertThat(repository().delete("ollama", "gemma").isOk()).isTrue();

        assertThat(repository().find("ollama", "gemma").data()).isEmpty();
        assertThat(repository().find("ollama", "other").data()).isPresent();
    }

    @Test
    void delete_unknownModel_isStillOk() {
        assertThat(repository().delete("ollama", "none").isOk()).isTrue();
    }

    @Test
    void update_nothingSaved_appliesTheChangeToTheDefaults() {
        final var updated = repository()
                .update("ollama", "gemma", s -> s.withMeasuredSpeed(CallKind.DRAFT, 1.2, MeasuredFrom.TEST));

        assertThat(updated.data())
                .isEqualTo(ModelSettings.defaults("ollama", "gemma")
                        .withMeasuredSpeed(CallKind.DRAFT, 1.2, MeasuredFrom.TEST));
        assertThat(repository().find("ollama", "gemma").data()).hasValue(updated.data());
    }

    @Test
    void update_existingRecord_keepsEveryOtherField() {
        repository().save(sample("ollama", "gemma"));

        repository().update("ollama", "gemma", s -> s.withMeasuredSpeed(CallKind.DRAFT, 0.8, MeasuredFrom.RUN));

        assertThat(repository().find("ollama", "gemma").data())
                .hasValue(sample("ollama", "gemma").withMeasuredSpeed(CallKind.DRAFT, 0.8, MeasuredFrom.RUN));
    }

    // Updating one call kind's speed must leave another kind's stored speed alone, in both adapters.
    @Test
    void update_secondsPerCall_keptPerKind() {
        repository().save(sample("ollama", "gemma").withMeasuredSpeed(CallKind.PROOFREAD, 6.0, MeasuredFrom.RUN));

        repository().update("ollama", "gemma", s -> s.withMeasuredSpeed(CallKind.DRAFT, 0.8, MeasuredFrom.RUN));

        final ModelSettings found = repository().find("ollama", "gemma").data().orElseThrow();
        assertThat(found.speedOf(CallKind.DRAFT)).hasValue(new MeasuredSpeed(0.8, MeasuredFrom.RUN));
        assertThat(found.speedOf(CallKind.PROOFREAD)).hasValue(new MeasuredSpeed(6.0, MeasuredFrom.RUN));
    }

    // IF Send again dropped more than the named row, THEN the other refusals and the settings would be lost with it.
    @Test
    void update_withoutRefusal_removesRowKeepsOthers() {
        final RefusedSetting topP = new RefusedSetting("top_p", LocalDate.of(2026, 10, 21), "omitted");
        final ModelSettings base = sample("ollama", "gemma");
        repository()
                .save(new ModelSettings(
                        "ollama",
                        "gemma",
                        base.temperatureChoice(),
                        base.temperature(),
                        base.contextWindow(),
                        base.maxOutput(),
                        base.outputField(),
                        List.of(base.refused().get(0), topP),
                        base.secondsPerCall(),
                        ProbeVerdicts.none(),
                        ThinkingOverride.RECOMMENDED,
                        SamplingOverride.profileDefault()));

        repository().update("ollama", "gemma", stored -> stored.withoutRefusal("temperature"));

        assertThat(repository().find("ollama", "gemma").data()).hasValueSatisfying(found -> {
            assertThat(found.refused()).containsExactly(topP);
            assertThat(found.contextWindow()).isEqualTo(8192);
            assertThat(found.speedOf(CallKind.DRAFT).map(MeasuredSpeed::seconds))
                    .hasValue(2.5);
        });
    }

    // IF two writers each read, changed and saved, THEN one change would be lost; update must apply both.
    @Test
    void update_twoWritersAtOnce_neitherChangeIsLost() throws Exception {
        final var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            final var refusal = pool.submit(() -> repository().update("ollama", "gemma", s -> {
                final var rows = new java.util.ArrayList<>(s.refused());
                rows.add(new RefusedSetting("top_p", LocalDate.of(2026, 10, 21), "left it out"));
                return s.withRefused(rows);
            }));
            final var speed = pool.submit(() -> repository()
                    .update("ollama", "gemma", s -> s.withMeasuredSpeed(CallKind.DRAFT, 0.9, MeasuredFrom.RUN)));
            refusal.get();
            speed.get();
        } finally {
            pool.shutdown();
        }

        final ModelSettings found = repository().find("ollama", "gemma").data().orElseThrow();
        assertThat(found.refused()).extracting(RefusedSetting::name).containsExactly("top_p");
        assertThat(found.speedOf(CallKind.DRAFT).map(MeasuredSpeed::seconds)).hasValue(0.9);
    }
}
