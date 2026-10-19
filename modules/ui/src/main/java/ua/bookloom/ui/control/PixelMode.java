package ua.bookloom.ui.control;

/** How a trackpad's pixel events are applied; only the replay tests ever choose {@link #NATIVE}. */
enum PixelMode {

    /** Added up and applied once per pulse by {@link PixelFlush}; the event is consumed. */
    BATCH,

    /** Left untouched for JavaFX to apply as each event comes; the baseline the replay metrics compare against. */
    NATIVE
}
