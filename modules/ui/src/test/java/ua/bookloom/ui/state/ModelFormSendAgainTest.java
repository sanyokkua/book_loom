package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;

/** Send again forgets one refusal at once, whatever else the form holds. */
class ModelFormSendAgainTest extends ModelFormTestBase {

    @Test
    void sendAgain_temperatureRow_removedFromStoreAndList() throws Exception {
        refuse(GPT_OSS, temperatureRefusal());
        buildForm();
        select(GPT_OSS);
        assertThat(onFx(() -> form.refused().size())).isEqualTo(1);

        sendAgain("temperature");

        assertThat(onFx(() -> form.refused().size())).isZero();
        assertThat(stored(GPT_OSS).refused()).isEmpty();
    }

    // IF Send again also saved or dropped the typing, THEN one click would change settings the person is still editing.
    @Test
    void sendAgain_whileDirty_draftKept() throws Exception {
        refuse(GPT_OSS, temperatureRefusal());
        buildForm();
        select(GPT_OSS);
        edit(draft -> draft.withContext(true, "4096", null));

        sendAgain("temperature");

        assertThat(draft().contextText()).isEqualTo("4096");
        assertThat(dirty()).isTrue();
        assertThat(stored(GPT_OSS).contextWindow()).isNull();
    }

    @Test
    void sendAgain_storeFails_keepsTheRowAndFlagsIt() throws Exception {
        refuse(GPT_OSS, temperatureRefusal());
        buildForm();
        select(GPT_OSS);
        modelSettings.failUpdate();

        sendAgain("temperature");

        assertThat(onFx(() -> form.refused().size())).isEqualTo(1);
        assertThat(onFx(() -> form.refusedFailed().get())).isTrue();
    }
}
