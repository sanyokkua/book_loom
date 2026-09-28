package ua.bookloom.pipeline.prompt;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.PromptName.Slots;

/**
 * Loads every declared {@code *.prompt} template once and renders it. A template that uses an undeclared slot or lacks
 * a required one stops the injector, so the model is never sent a literal {@code {{summary}}}.
 */
@Slf4j
public final class PromptTemplates {

    private static final Pattern MARKER = Pattern.compile("\\{\\{([#/]?)([A-Za-z][A-Za-z0-9]*)}}");
    private static final Pattern BLOCK =
            Pattern.compile("^\\{\\{#(\\w+)}}\\n(.*?)^\\{\\{/\\1}}\\n(\\n)?", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)}}");

    /** Opens a template file by name; {@code null} when it does not exist. */
    @FunctionalInterface
    interface ResourceLoader {
        @Nullable
        InputStream open(String fileName);
    }

    private final Map<PromptName, Template> systems = new EnumMap<>(PromptName.class);
    private final Map<PromptName, Template> users = new EnumMap<>(PromptName.class);

    /** Loads the bundled templates. */
    @Inject
    public PromptTemplates() {
        this(fileName -> PromptTemplates.class.getResourceAsStream(fileName));
    }

    PromptTemplates(final ResourceLoader loader) {
        Objects.requireNonNull(loader, "loader");
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
        logRender(template, values);
        final String withBlocks = BLOCK.matcher(template.text())
                .replaceAll(block -> Matcher.quoteReplacement(
                        values.getOrDefault(block.group(1), "").isEmpty()
                                ? ""
                                : block.group(2) + (block.group(3) == null ? "" : block.group(3))));
        return SLOT.matcher(withBlocks)
                .replaceAll(slot -> Matcher.quoteReplacement(values.getOrDefault(slot.group(1), "")));
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
