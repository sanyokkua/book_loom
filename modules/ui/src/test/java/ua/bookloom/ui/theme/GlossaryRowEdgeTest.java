package ua.bookloom.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A row the last operation changed is marked by a coloured left edge; every row keeps an edge of the same width, so the
 * mark recolours it and never pushes the row's cells to the right.
 */
class GlossaryRowEdgeTest {

    private static final Pattern WIDTH = Pattern.compile("-fx-border-width:\\s*([^;]+);");

    private static String borderWidth(final String selector) {
        final Map<String, String> rules = ThemeTestSupport.rules(ThemeTestSupport.themeCss());
        final Matcher width = WIDTH.matcher(rules.getOrDefault(selector, ""));
        return width.find() ? width.group(1).strip() : "";
    }

    @Test
    void stylesheet_changedRow_keepsTheEdgeWidthOfEveryRow() {
        assertThat(borderWidth(".glossary-table .table-row-cell")).isEqualTo("0 0 1px 3px");
        assertThat(borderWidth(".glossary-table .table-row-cell:changed")).isIn("", "0 0 1px 3px");
    }
}
