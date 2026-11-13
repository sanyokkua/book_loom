package ua.bookloom.ui.theme;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The names of the stylesheet's shared style classes, written once so a rename is made in one place. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StyleClasses {

    public static final String BTN_SECONDARY = "btn-secondary";
    public static final String BTN_GHOST = "btn-ghost";
    public static final String MUTED = "muted";
    public static final String DIALOG_TEXT = "dialog-text";
    public static final String DIALOG_SUB = "dialog-sub";
    public static final String CARD_TITLE = "card-title";
    public static final String BTN_PRIMARY = "btn-primary";
}
