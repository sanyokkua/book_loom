package ua.bookloom.api.settings;

/** Whether a call sends only temperature (the server's own sampling) or BookLoom's tuned profile per call kind. */
public enum SamplingSetting {
    PROGRAM_DEFAULTS,
    TUNED
}
