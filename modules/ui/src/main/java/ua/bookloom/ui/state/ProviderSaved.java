package ua.bookloom.ui.state;

/** What a save was about, carried from the request to its outcome on the FX thread. */
record ProviderSaved(String id, boolean adding, boolean current, ProviderDraft typed) {}
