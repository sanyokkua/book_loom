package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelQuantChips;

/** A row of the Models chooser carries the quant chip once a quant is known, and no graphic at all before. */
class ModelEntryCellTest extends FxTestBase {

    private static final ModelEntry PHI =
            new ModelEntry("ollama", "Ollama", ProviderKind.OLLAMA, "phi4-mini", ServerEngine.OLLAMA);
    private static final ModelEntry LLAMA =
            new ModelEntry("ollama", "Ollama", ProviderKind.OLLAMA, "llama3.1:8b", ServerEngine.OLLAMA);

    private final ModelQuantChips chips = chipsOver(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

    private static ModelQuantChips chipsOver(final java.util.concurrent.ExecutorService io) {
        final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();
        return new ModelQuantChips(capabilities, new ua.bookloom.ui.state.AdviceReader(capabilities, io), io);
    }

    private static void fx(final Runnable action) {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    private ModelEntryCell cell() {
        return onFx(() -> new ModelEntryCell(chips, entry -> entry.modelId() + " · Ollama", EnglishMessages.create()));
    }

    // IF a model with a quant showed no chip, THEN the six downloads of one model could not be told apart.
    @Test
    void updateItem_quantKnown_showsLabelAndTheChip() {
        fx(() -> chips.publishFacts(
                new ModelSelection("ollama", "phi4-mini"),
                new QuantAdvice("Q4_K_M", null, null, false, false, false, null, null, QuantTier.ACCEPTABLE, "test")));
        final ModelEntryCell cell = cell();

        fx(() -> cell.updateItem(PHI, false));

        assertThat(onFx(cell::getText)).isEqualTo("phi4-mini · Ollama");
        assertThat(((Label) onFx(cell::getGraphic)).getText()).isEqualTo("Q4_K_M · acceptable");
    }

    // IF a model without a quant got an invisible chip, THEN a hidden node would still be found and still take room.
    @Test
    void updateItem_noQuant_hasNoGraphicAtAll() {
        final ModelEntryCell cell = cell();

        fx(() -> cell.updateItem(LLAMA, false));

        assertThat(onFx(cell::getGraphic)).isNull();
    }

    // IF the chip arrived after the row was drawn and the row stayed bare, THEN a slow reading would never show.
    @Test
    void chip_publishedAfterTheRowWasDrawn_appearsInTheRow() {
        final ModelEntryCell cell = cell();
        fx(() -> cell.updateItem(PHI, false));
        assertThat(onFx(cell::getGraphic)).isNull();

        fx(() -> chips.publishFacts(
                new ModelSelection("ollama", "phi4-mini"),
                new QuantAdvice("Q4_K_M", null, null, false, false, false, null, null, null, "test")));

        assertThat(((Label) onFx(cell::getGraphic)).getText()).isEqualTo("Q4_K_M");
    }
}
