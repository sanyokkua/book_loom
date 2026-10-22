package ua.bookloom.ui.screen;

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.AddRecurringTermDialog;
import ua.bookloom.ui.dialog.AddTermDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CsvFileChooser;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.RecurringTerms;

/**
 * Builds the Glossary card and the Recurring terms card of Names &amp; style from their words, toolbars and rows, each a
 * {@link TermTableCard}. While work in the background writes to the screen, {@code frozen} turns every editing control
 * of both cards off; reading, searching, sorting and exporting stay on.
 */
@Slf4j
final class NamesStyleCards {

    private final Messages messages;
    private final NamesStyleViewModel glossary;
    private final CsvFileChooser csvFiles;
    private final ModalHost modalHost;
    private final ObservableBooleanValue frozen;

    NamesStyleCards(
            final Messages messages,
            final NamesStyleViewModel glossary,
            final CsvFileChooser csvFiles,
            final ModalHost modalHost,
            final ObservableBooleanValue frozen) {
        this.messages = messages;
        this.glossary = glossary;
        this.csvFiles = csvFiles;
        this.modalHost = modalHost;
        this.frozen = frozen;
    }

    TermTableCard<GlossaryEntry> glossaryCard() {
        return new TermTableCard<>(
                messages,
                glossary,
                csvFiles,
                "names-style",
                glossaryTexts(),
                glossaryToolbar(),
                GlossaryTable.rows(messages, glossary, frozen));
    }

    private static TermTableCard.Texts glossaryTexts() {
        return new TermTableCard.Texts(
                "names-style-glossary-title",
                MessageKey.NAMES_STYLE_GLOSSARY_TITLE,
                MessageKey.NAMES_STYLE_GLOSSARY_NOTE,
                null,
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_ADD, MessageKey.NAMES_STYLE_ADD_TIP),
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_IMPORT, MessageKey.NAMES_STYLE_IMPORT_TIP),
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_EXPORT, MessageKey.NAMES_STYLE_EXPORT_TIP),
                MessageKey.NAMES_STYLE_EMPTY,
                MessageKey.NAMES_STYLE_EMPTY_FILTERED,
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_EMPTY_SCAN, MessageKey.NAMES_STYLE_EMPTY_SCAN_TIP));
    }

    private TermTableCard.Toolbar glossaryToolbar() {
        final ModelActionBar.Words words = new ModelActionBar.Words(
                MessageKey.NAMES_STYLE_MODEL_SCAN,
                MessageKey.NAMES_STYLE_MODEL_SCAN_TIP,
                MessageKey.NAMES_STYLE_REVIEW,
                MessageKey.NAMES_STYLE_REVIEW_TIP,
                MessageKey.NAMES_STYLE_TRANSLATE,
                MessageKey.NAMES_STYLE_TRANSLATE_TIP);
        return new TermTableCard.Toolbar(
                words,
                new ModelActionBar.Actions(glossary::modelScan, glossary::review, glossary::translate),
                this::openAddTerm,
                glossary::importCsv,
                glossary::exportCsv,
                "glossary.csv",
                acceptAll(),
                frozen);
    }

    TermTableCard<LexiconEntry> recurringCard() {
        return new TermTableCard<>(
                messages,
                glossary,
                csvFiles,
                "recurring",
                recurringTexts(),
                recurringToolbar(),
                RecurringTermsCard.rows(messages, glossary, frozen));
    }

    private static TermTableCard.Texts recurringTexts() {
        return new TermTableCard.Texts(
                "recurring-title",
                MessageKey.RECURRING_TITLE,
                MessageKey.RECURRING_NOTE,
                new TermTableCard.Help(MessageKey.RECURRING_HELP_LINK, MessageKey.RECURRING_HELP),
                new TermTableCard.Phrase(MessageKey.RECURRING_ADD, MessageKey.RECURRING_ADD_TIP),
                new TermTableCard.Phrase(MessageKey.RECURRING_IMPORT, MessageKey.RECURRING_IMPORT_TIP),
                new TermTableCard.Phrase(MessageKey.RECURRING_EXPORT, MessageKey.RECURRING_EXPORT_TIP),
                MessageKey.RECURRING_EMPTY,
                MessageKey.NAMES_STYLE_EMPTY_FILTERED,
                null);
    }

    private TermTableCard.Toolbar recurringToolbar() {
        final RecurringTerms recurring = glossary.recurring();
        final ModelActionBar.Words words = new ModelActionBar.Words(
                MessageKey.RECURRING_MODEL_SCAN,
                MessageKey.RECURRING_MODEL_SCAN_TIP,
                MessageKey.RECURRING_MODEL_REVIEW,
                MessageKey.RECURRING_MODEL_REVIEW_TIP,
                MessageKey.RECURRING_SUGGEST,
                MessageKey.RECURRING_SUGGEST_TIP);
        return new TermTableCard.Toolbar(
                words,
                new ModelActionBar.Actions(recurring::scan, recurring::review, recurring::translate),
                this::openAddRecurring,
                recurring::importCsv,
                recurring::exportCsv,
                "recurring-terms.csv",
                null,
                frozen);
    }

    // Shown only while a row holds a suggestion, and off while a model action could replace the suggestions it would
    // accept, or while the screen is read-only.
    private Button acceptAll() {
        final Button acceptAll = Tips.install(
                messages,
                new Button(messages.get(MessageKey.NAMES_STYLE_ACCEPT_ALL)),
                MessageKey.NAMES_STYLE_ACCEPT_ALL_TIP);
        acceptAll.setId("names-style-accept-all");
        acceptAll.getStyleClass().add("btn-ghost");
        acceptAll.setMinWidth(Region.USE_PREF_SIZE);
        acceptAll.setMaxHeight(Double.MAX_VALUE);
        acceptAll.setOnAction(event -> {
            log.debug("names-style-accept-all pressed");
            glossary.acceptAll();
        });
        acceptAll.disableProperty().bind(glossary.busy().or(frozen));
        acceptAll.visibleProperty().bind(glossary.suggestedCount().greaterThan(0));
        acceptAll.managedProperty().bind(acceptAll.visibleProperty());
        return acceptAll;
    }

    private void openAddTerm() {
        modalHost.show(new AddTermDialog(messages, glossary, modalHost::hide).card(), false);
    }

    private void openAddRecurring() {
        modalHost.show(new AddRecurringTermDialog(messages, glossary.recurring(), modalHost::hide).card(), false);
    }
}
