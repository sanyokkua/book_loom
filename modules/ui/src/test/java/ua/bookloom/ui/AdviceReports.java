package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelKind;
import ua.bookloom.api.llm.ModelNotes;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.ReasoningControl;

/** Builds the reports and language advice the advice-line tests script, from the few facts each case is about. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdviceReports {

    /** A model's report with notes: who it is, how it was matched, its quant tier and any warnings. */
    public static ModelReport report(
            final ModelReport.Match match,
            final @Nullable String baseModel,
            final ModelIdentity identity,
            final ModelNotes notes,
            final @Nullable QuantTier tier,
            final List<String> warnings) {
        return new ModelReport(
                null,
                null,
                identity,
                match,
                ModelReport.UNKNOWN_MODEL,
                warnings,
                ReasoningControl.UNKNOWN,
                null,
                tier == null
                        ? null
                        : new QuantAdvice("Q4_K_M", null, null, false, false, false, "4-bit", null, tier, "test"),
                baseModel,
                notes);
    }

    /** Who a model is, as far as the lines read it. */
    public static ModelIdentity identity(
            final String family, final @Nullable Double sizeB, final @Nullable String quantLabel) {
        return new ModelIdentity(
                family, null, null, sizeB, null, null, List.of(), ModelKind.LLM, null, null, quantLabel, List.of());
    }

    /** Notes with no server quant, no library tag and no measurement. */
    public static ModelNotes notes(final String label, final String familyLabel) {
        return new ModelNotes(label, familyLabel, null, null, false);
    }

    /** Advice for a pair in which each side has the given state and BookLoom tested nothing. */
    public static LanguageAdvice advice(
            final LanguageState source, final LanguageState target, final boolean tested, final String family) {
        return new LanguageAdvice(source, target, false, tested, family, null, List.of());
    }
}
