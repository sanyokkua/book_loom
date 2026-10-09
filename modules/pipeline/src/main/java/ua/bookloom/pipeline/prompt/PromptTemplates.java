package ua.bookloom.pipeline.prompt;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.pipeline.prompt.PromptName.Slots;

/**
 * Loads every declared {@code *.prompt} template once and renders it. A template that uses an undeclared slot or lacks
 * a required one stops the injector, so the model is never sent a literal {@code {{summary}}}.
 */
@Slf4j
public final class PromptTemplates {

    private static final Pattern MARKER = Pattern.compile("\\{\\{([#/]?)([A-Za-z][A-Za-z0-9]*)}}");
    /** An optional block: group 1 its slot, group 2 its body, group 3 the blank line after it, kept with the block. */
    static final Pattern BLOCK =
            Pattern.compile("^\\{\\{#(\\w+)}}\\n(.*?)^\\{\\{/\\1}}\\n(\\n)?", Pattern.MULTILINE | Pattern.DOTALL);

    /** A plain slot: group 1 its name. */
    static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)}}");

    /** Opens a template file by name; {@code null} when it does not exist. */
    @FunctionalInterface
    interface ResourceLoader {
        @Nullable
        InputStream open(String fileName);
    }

    private static final String EXAMPLES = "examples";
    private static final String LANGUAGE_RULES = "languageRules";
    private static final String TOKEN_RULES = "tokenRules";
    private static final String NO_TOKEN_RULES = "noTokenRules";
    private static final String GATE_OPEN = "on";

    private final Map<PromptName, Template> systems = new EnumMap<>(PromptName.class);
    private final Map<PromptName, Template> users = new EnumMap<>(PromptName.class);
    private final LanguageRules rules;

    /** Loads the bundled templates. */
    @Inject
    public PromptTemplates() {
        this(fileName -> PromptTemplates.class.getResourceAsStream(fileName), LanguageRules.forEnvironment());
    }

    PromptTemplates(final ResourceLoader loader) {
        this(loader, new LanguageRules(loader, false));
    }

    PromptTemplates(final ResourceLoader loader, final LanguageRules rules) {
        Objects.requireNonNull(loader, "loader");
        this.rules = Objects.requireNonNull(rules, "rules");
        for (final PromptName name : PromptName.values()) {
            name.systemSlots().ifPresent(slots -> systems.put(name, load(loader, name, "system", slots)));
            users.put(name, load(loader, name, "user", name.userSlots()));
        }
        log.debug(
                "Loaded {} prompt templates: {}",
                systems.size() + users.size(),
                Stream.concat(systems.values().stream(), users.values().stream())
                        .map(Template::fileName)
                        .toList());
    }

    /** Renders the system template of {@code name}; the map holds every required slot and only declared ones. */
    public String renderSystem(final PromptName name, final Map<String, String> values) {
        final Template template = systems.get(Objects.requireNonNull(name, "name"));
        if (template == null) {
            throw new IllegalArgumentException(name + " has no system template");
        }
        return render(template, values);
    }

    /**
     * Renders the system template of {@code name} from a run's call frame: its four frame slots, plus the language
     * rules and the bundled examples for the frame's language pair when the template declares them.
     *
     * @param name the non-null call
     * @param frame the non-null run's language pair, style sheet and foreign-passage policy
     * @return the rendered system message
     */
    public String renderSystem(final PromptName name, final CallFrame frame) {
        return renderSystem(name, frame, Map.of());
    }

    /**
     * Renders the system template of {@code name} from a run's call frame and the call's own further slots, such as
     * the suggestion call's name rule.
     *
     * @param name the non-null call
     * @param frame the non-null run's language pair, style sheet and foreign-passage policy
     * @param extra the non-null further slot values, each declared by the template
     * @return the rendered system message
     */
    public String renderSystem(final PromptName name, final CallFrame frame, final Map<String, String> extra) {
        return renderSystem(name, systemValues(name, frame, extra));
    }

    /**
     * The system slots that choose the token rules of a draft or batch call: a call whose text holds no
     * {@code ⟦gN⟧} token is sent the rules without the token paragraphs, so a small model is not taught a markup it
     * has nothing to copy. A call that names neither slot gets the token rules.
     *
     * @param hasTokens whether any text the call translates holds a token
     * @return never null; the slot values to pass as {@code extra}
     */
    public static Map<String, String> tokenGate(final boolean hasTokens) {
        return hasTokens
                ? Map.of(TOKEN_RULES, GATE_OPEN, NO_TOKEN_RULES, "")
                : Map.of(TOKEN_RULES, "", NO_TOKEN_RULES, GATE_OPEN);
    }

    /** Whether a block slot only chooses between two wordings of the fixed rules and so is no part of its own. */
    static boolean isRuleGate(final String slot) {
        return TOKEN_RULES.equals(slot) || NO_TOKEN_RULES.equals(slot);
    }

    /**
     * As {@link #sectionsOf(PromptName, CallFrame, Map, Map)} with the system message's default slots.
     *
     * @param name the non-null call
     * @param frame the non-null run's language pair, style sheet and foreign-passage policy
     * @param userValues the non-null user slot values, as {@link #renderUser} takes them
     * @return never null; the parts with a value
     */
    public List<PromptSection> sectionsOf(
            final PromptName name, final CallFrame frame, final Map<String, String> userValues) {
        return sectionsOf(name, frame, Map.of(), userValues);
    }

    /**
     * The filled parts of {@code name}'s prompt as {@link #renderSystem(PromptName, CallFrame)} and
     * {@link #renderUser} send them: the system message's parts first, then the user message's, each in its
     * template's order.
     *
     * @param name the non-null call
     * @param frame the non-null run's language pair, style sheet and foreign-passage policy
     * @param extra the non-null further system slot values, as {@link #renderSystem(PromptName, CallFrame, Map)} takes
     *     them
     * @param userValues the non-null user slot values, as {@link #renderUser} takes them
     * @return never null; the parts with a value, a part left empty not being sent
     */
    public List<PromptSection> sectionsOf(
            final PromptName name,
            final CallFrame frame,
            final Map<String, String> extra,
            final Map<String, String> userValues) {
        final List<PromptSection> sections = new ArrayList<>();
        final Template system = systems.get(Objects.requireNonNull(name, "name"));
        if (system != null) {
            final Map<String, String> values = systemValues(name, frame, extra);
            check(system, values);
            sections.addAll(PromptSections.of(system.text(), values, PromptSection.Origin.SYSTEM));
        }
        sections.addAll(userSectionsOf(name, userValues));
        log.debug(
                "Prompt sections name={} slots={}",
                name,
                sections.stream().map(PromptSection::slot).toList());
        return sections;
    }

    /**
     * The filled parts of {@code name}'s user message as {@link #renderUser} sends it, in the template's order.
     *
     * @param name the non-null call
     * @param values the non-null slot values, as {@link #renderUser} takes them
     * @return never null; the parts with a value
     */
    public List<PromptSection> userSectionsOf(final PromptName name, final Map<String, String> values) {
        final Template template = Objects.requireNonNull(users.get(Objects.requireNonNull(name, "name")));
        check(template, values);
        return PromptSections.of(template.text(), values, PromptSection.Origin.USER);
    }

    private Map<String, String> systemValues(
            final PromptName name, final CallFrame frame, final Map<String, String> extra) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(extra, "extra");
        final Map<String, String> values = new HashMap<>(frame.systemSlotValues());
        values.putAll(extra);
        if (declares(name, TOKEN_RULES) && !extra.containsKey(TOKEN_RULES)) {
            values.putAll(tokenGate(true));
        }
        if (declares(name, EXAMPLES)) {
            values.put(EXAMPLES, examplesFor(name, frame));
        }
        if (declares(name, LANGUAGE_RULES)) {
            values.put(
                    LANGUAGE_RULES,
                    rules.section(frame.sourceLanguage(), frame.targetLanguage(), name.reviewsTranslation()));
        }
        return values;
    }

    private String examplesFor(final PromptName name, final CallFrame frame) {
        final String source = frame.sourceLanguage();
        final String target = frame.targetLanguage();
        if (name.showsNameExamples()) {
            return rules.nameExamples(source, target);
        }
        return name == PromptName.DRAFT_BATCH_JSON
                ? BatchExamples.render(rules.batchExamples(source, target))
                : rules.examples(source, target);
    }

    private static boolean declares(final PromptName name, final String slot) {
        return Objects.requireNonNull(name, "name")
                .systemSlots()
                .map(slots -> slots.declares(slot))
                .orElse(false);
    }

    /** Renders the user template of {@code name}; the map holds every required slot and only declared ones. */
    public String renderUser(final PromptName name, final Map<String, String> values) {
        return render(Objects.requireNonNull(users.get(Objects.requireNonNull(name, "name"))), values);
    }

    private static Template load(
            final ResourceLoader loader, final PromptName name, final String part, final Slots slots) {
        final String fileName = name.resourceBaseName() + "." + part + ".prompt";
        final String text = read(loader, fileName);
        final Set<String> plain = new HashSet<>();
        final Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            final String slot = matcher.group(2);
            if (!slots.declares(slot)) {
                throw new IllegalStateException(
                        "Template " + fileName + " uses undeclared slot '" + slot + "' for " + name);
            }
            if (matcher.group(1).isEmpty()) {
                plain.add(slot);
            }
        }
        for (final String slot : new TreeSet<>(slots.required())) {
            if (!plain.contains(slot)) {
                throw new IllegalStateException("Template " + fileName + " lacks required slot '" + slot + "'");
            }
        }
        return new Template(fileName, text, slots);
    }

    private static String read(final ResourceLoader loader, final String fileName) {
        try (InputStream stream = loader.open(fileName)) {
            if (stream == null) {
                throw new IllegalStateException("Template " + fileName + " is missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new IllegalStateException("Template " + fileName + " could not be read", cause);
        }
    }

    private static String render(final Template template, final Map<String, String> values) {
        check(template, values);
        logRender(template, values);
        final String withBlocks = BLOCK.matcher(template.text())
                .replaceAll(block -> Matcher.quoteReplacement(
                        values.getOrDefault(block.group(1), "").isEmpty()
                                ? ""
                                : block.group(2) + (block.group(3) == null ? "" : block.group(3))));
        return fill(withBlocks, values);
    }

    /** Puts each slot's value in place of its marker; a slot with no value becomes empty. */
    static String fill(final String part, final Map<String, String> values) {
        return SLOT.matcher(part).replaceAll(slot -> Matcher.quoteReplacement(values.getOrDefault(slot.group(1), "")));
    }

    private static void check(final Template template, final Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        final Slots slots = template.slots();
        for (final String key : values.keySet()) {
            if (!slots.declares(key)) {
                throw new IllegalArgumentException("Template " + template.fileName() + " has no slot '" + key + "'");
            }
        }
        for (final String slot : slots.required()) {
            if (!values.containsKey(slot)) {
                throw new IllegalArgumentException("Template " + template.fileName() + " needs slot '" + slot + "'");
            }
        }
    }

    private static void logRender(final Template template, final Map<String, String> values) {
        if (!log.isDebugEnabled()) {
            return;
        }
        final Set<String> filled = new TreeSet<>();
        final Set<String> empty = new TreeSet<>();
        for (final String slot : new TreeSet<>(union(template.slots()))) {
            (values.getOrDefault(slot, "").isEmpty() ? empty : filled).add(slot);
        }
        log.debug("Rendering template {} filledSlots={} emptySlots={}", template.fileName(), filled, empty);
    }

    private static Set<String> union(final Slots slots) {
        final Set<String> all = new HashSet<>(slots.required());
        all.addAll(slots.optional());
        return all;
    }

    private record Template(String fileName, String text, Slots slots) {}
}
