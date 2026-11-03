package ua.bookloom.ui.state;

import ua.bookloom.ui.ScriptedModelCapabilities;

/** Quant chips over a port that knows nothing, read on the calling thread, for tests that build a view model. */
final class QuantChipsFixture {

    private QuantChipsFixture() {}

    static ModelQuantChips idle() {
        return new ModelQuantChips(ScriptedModelCapabilities.idle(), new DirectExecutor());
    }
}
