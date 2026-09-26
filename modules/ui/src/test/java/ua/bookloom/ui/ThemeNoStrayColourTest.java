package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * No colour value is written anywhere but inside a value block of the catalogue.
 *
 * <p>A component that names a colour of its own defeats the second theme: the dark block swaps values on
 * {@code .root} and never reaches it. The check reads the stylesheet's rules and flags any that holds a hex, an
 * {@code rgb(}/{@code rgba(} or a named colour and is not one of the two value blocks or an elevation rule.
 * Its negative controls prove the detector can fail, so the empty result on {@code theme.css} means something.
 */
class ThemeNoStrayColourTest {

    private static final Set<String> VALUE_BLOCKS = Set.of(".root", ".root.theme-dark");
    private static final Set<String> ELEVATION_SELECTORS = Set.of(
            ".root .elevation-sm",
            ".root .elevation",
            ".root .elevation-lg",
            ".root.theme-dark .elevation-sm",
            ".root.theme-dark .elevation",
            ".root.theme-dark .elevation-lg");

    private static final Pattern SEMICOLON = Pattern.compile(";");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern FUNCTION = Pattern.compile("\\brgba?\\s*\\(");
    private static final Pattern NAMED = Pattern.compile("(?<![\\w-])(white|black|red|green|blue|yellow|orange|"
            + "purple|pink|brown|gray|grey|silver|gold|cyan|magenta|navy|teal|maroon|olive|lime)(?![\\w-])");

    /**
     * The selectors of every rule that writes a colour anywhere the catalogue does not allow one.
     *
     * <p>Allowed, and nothing else: the two value blocks {@code .root} and {@code .root.theme-dark}, and the six
     * elevation rules, where a literal may appear only inside {@code -fx-effect}. Matching the selector text whole
     * (not by prefix) means {@code .root .card}, {@code .rooted} and {@code .root, .app-shell} get no exemption.
     *
     * @param css the stylesheet text
     * @return the offending selectors, empty when no colour is written outside the allowed places
     */
    static List<String> strayColourRules(final String css) {
        final List<String> stray = new ArrayList<>();
        for (final Map.Entry<String, String> rule : ThemeTestSupport.rules(css).entrySet()) {
            if (!isAllowed(rule.getKey(), rule.getValue())) {
                stray.add(rule.getKey());
            }
        }
        return stray;
    }

    private static boolean isAllowed(final String selector, final String body) {
        if (VALUE_BLOCKS.contains(selector)) {
            return true;
        }
        if (ELEVATION_SELECTORS.contains(selector)) {
            return !holdsColourLiteral(withoutEffect(body));
        }
        return !holdsColourLiteral(body);
    }

    private static String withoutEffect(final String body) {
        final StringBuilder kept = new StringBuilder();
        for (final String declaration : SEMICOLON.splitAsStream(body).toList()) {
            if (!declaration.trim().startsWith("-fx-effect")) {
                kept.append(declaration).append(';');
            }
        }
        return kept.toString();
    }

    private static boolean holdsColourLiteral(final String body) {
        for (final String declaration : SEMICOLON.splitAsStream(body).toList()) {
            final int colon = declaration.indexOf(':');
            final String value = colon < 0 ? "" : declaration.substring(colon + 1);
            if (HEX.matcher(value).find()
                    || FUNCTION.matcher(value).find()
                    || NAMED.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    // IF theme.css is scanned for colour literals outside the .root value blocks, THEN there are none.
    @Test
    void themeCss_colourLiteralsOutsideRootBlocks_areNone() {
        assertThat(strayColourRules(ThemeTestSupport.themeCss()))
                .as("rules that write a colour of their own instead of referring to a role")
                .isEmpty();
    }

    // IF a rule outside .root writes a colour in any form, THEN the detector flags it (negative control).
    @ParameterizedTest
    @ValueSource(
            strings = {
                ".app-shell { -fx-background-color: #abc }",
                ".app-shell { -fx-background-color: #a58075; -fx-border-width: 1 }",
                ".app-shell { -fx-text-fill: white }",
                ".card { -fx-border-color: rgba(1,2,3,.5) }",
                ".card { -fx-text-fill: rgb(1, 2, 3) }",
                ".rooted { -fx-background-color: #abc }",
                ".root .card { -fx-background-color: #abc }",
                ".root.theme-dark .card { -fx-text-fill: white }",
                ".root .elevation { -fx-background-color: #abc }",
                ".root, .app-shell { -fx-background-color: #abc }",
            })
    void strayColourRules_colourLiteralOutsideRoot_isFlagged(final String css) {
        assertThat(strayColourRules(css)).hasSize(1);
    }

    // IF colours sit only inside .root blocks and everything else refers to a role, THEN nothing is flagged.
    @ParameterizedTest
    @ValueSource(
            strings = {
                ".root { -color-x: #abc; -color-y: white } .app-shell { -fx-background-color: -color-x }",
                ".root.theme-dark { -color-x: rgba(0,0,0,.5) } .app-shell { -fx-text-fill: -color-text }",
                ".root .elevation { -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,.3), 2, 0, 0, 1) }",
                ".app-shell { -fx-border-color: -color-brand-sand; -fx-font-size: 14px }",
            })
    void strayColourRules_rolesOnlyOutsideRoot_isNotFlagged(final String css) {
        assertThat(strayColourRules(css)).isEmpty();
    }
}
