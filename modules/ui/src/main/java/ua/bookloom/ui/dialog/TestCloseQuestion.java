package ua.bookloom.ui.dialog;

/** Asks before the provider test card is closed while its test runs, for {@link ProviderTestView}. */
@FunctionalInterface
interface TestCloseQuestion {

    /**
     * Asks.
     *
     * @param test the running test, worded
     * @param stop what stops it
     * @param keepRunning what lets it run on
     */
    void ask(String test, Runnable stop, Runnable keepRunning);
}
