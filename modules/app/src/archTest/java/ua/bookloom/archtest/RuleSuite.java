package ua.bookloom.archtest;

import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Discovers the boundary rules by <em>reflection</em> over {@link ArchitectureRules} rather than from a
 * hand-maintained list.
 *
 * <p>The distinction is the whole reason this class exists. A hand-maintained list can fall out of step with the
 * fields in two directions: a rule deleted from the list but left as a field becomes dead code nobody evaluates,
 * and a rule added as a field but forgotten in the list never runs. Reading the fields makes the suite and the
 * class the same thing, so {@code RuleSuiteCompletenessTest} comparing this against the spec's nine names is a
 * real check and not a tautology.
 */
final class RuleSuite {

    private RuleSuite() {
        // Reflection helper.
    }

    /** Every {@code public static final ArchRule} on {@link ArchitectureRules}, keyed by its spec name. */
    static Map<String, ArchRule> byName() {
        Map<String, ArchRule> rules = new TreeMap<>();
        for (Map.Entry<String, ArchRule> entry : byFieldName().entrySet()) {
            rules.put(nameOf(entry.getValue()), entry.getValue());
        }
        return rules;
    }

    /** The same rules, keyed by the Java field that declares them — used when a failure must name the field. */
    static Map<String, ArchRule> byFieldName() {
        Map<String, ArchRule> rules = new LinkedHashMap<>();
        for (Field field : ArchitectureRules.class.getDeclaredFields()) {
            if (isRuleField(field)) {
                rules.put(field.getName(), read(field));
            }
        }
        return rules;
    }

    /**
     * The spec name a rule declares, taken from the prefix of its description. Falls back to the whole
     * description so a rule that forgot the separator surfaces as an obviously wrong name rather than an
     * exception.
     */
    static String nameOf(ArchRule rule) {
        String description = rule.getDescription();
        int separator = description.indexOf(ArchitectureRules.NAME_SEPARATOR);
        return separator < 0 ? description : description.substring(0, separator);
    }

    private static boolean isRuleField(Field field) {
        int modifiers = field.getModifiers();
        return ArchRule.class.isAssignableFrom(field.getType())
                && Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers);
    }

    private static ArchRule read(Field field) {
        try {
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read boundary rule field " + field.getName(), e);
        }
    }
}
