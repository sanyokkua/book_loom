package ua.bookloom.persistence.sqlite;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.util.hash.HashUtil;

/**
 * The fingerprint of the JSON shape of every stored record. {@link JsonCodec} reads strictly, so a stored document
 * written before an {@code :api} record gained, lost or retyped a component cannot be read back; before the first
 * release the database is started fresh instead (ADR-0043), and this fingerprint, kept in the {@code meta} table, is
 * what tells {@link SqliteDatabase} that the file was written for other shapes.
 *
 * <p>The shape is what the JSON depends on and nothing else: a record is its components sorted by name, each with its
 * type; a nested record is described inline the same way and an enum by its constant names, so renaming or moving a
 * record changes nothing, while a component added, removed, renamed or retyped, or an enum constant changed, does.
 * Reflection reads only the public component list, so the text, and its SHA-256, is the same on every JVM run.
 */
@Slf4j
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StoredShapes {

    /**
     * Every type a document of the database is read into: the per-project documents, the rows' JSON, the brief and,
     * through {@link Project}, the enums of the projects table's columns.
     */
    static final List<Class<?>> ROOTS = List.of(
            Project.class,
            SegmentRecord.class,
            GlossaryDocument.class,
            LexiconDocument.class,
            RollingSummary.class,
            Deferral.class,
            RunRecord.class,
            TmEntry.class,
            ExportRecord.class,
            ProviderConfig.class,
            ModelSettings.class);

    private static final String CURRENT = fingerprint(ROOTS);

    /**
     * The fingerprint of the records this build stores.
     *
     * @return the SHA-256 of {@link #describe} over {@link #ROOTS}, as 64 lower-case hex digits
     */
    static String current() {
        return CURRENT;
    }

    /**
     * Fingerprints the JSON shapes of {@code roots}.
     *
     * @param roots the stored types, in a fixed order
     * @return the SHA-256 of their description, as 64 lower-case hex digits
     */
    static String fingerprint(final List<Class<?>> roots) {
        Objects.requireNonNull(roots, "roots");
        final String text = describe(roots);
        final String hash = HashUtil.sha256Hex(text.getBytes(StandardCharsets.UTF_8));
        log.debug("Stored shapes fingerprinted roots={} chars={} fingerprint={}", roots.size(), text.length(), hash);
        log.trace("Stored shapes {}", text);
        return hash;
    }

    /**
     * Describes the JSON shapes of {@code roots}, one per line.
     *
     * @param roots the stored types, in a fixed order
     * @return the description; never empty for a non-empty list
     */
    static String describe(final List<Class<?>> roots) {
        Objects.requireNonNull(roots, "roots");
        return roots.stream().map(root -> type(root, new ArrayDeque<>())).collect(Collectors.joining("\n"));
    }

    private static String type(final Type type, final Deque<Class<?>> open) {
        return switch (type) {
            case Class<?> c when c.isRecord() -> record(c, open);
            case Class<?> c when c.isEnum() -> enumeration(c);
            case Class<?> c when c.isArray() -> type(c.getComponentType(), open) + "[]";
            case ParameterizedType p -> type(p.getRawType(), open) + arguments(p, open);
            case GenericArrayType a -> type(a.getGenericComponentType(), open) + "[]";
            default -> type.getTypeName();
        };
    }

    /** A record that holds itself, directly or not, is named where it recurs, so the walk ends. */
    private static String record(final Class<?> c, final Deque<Class<?>> open) {
        if (open.contains(c)) {
            return "@" + c.getName();
        }
        open.push(c);
        final String components = Arrays.stream(c.getRecordComponents())
                .sorted(Comparator.comparing(RecordComponent::getName))
                .map(component -> component.getName() + ":" + type(component.getGenericType(), open))
                .collect(Collectors.joining(","));
        open.pop();
        return "{" + components + "}";
    }

    private static String enumeration(final Class<?> c) {
        return Arrays.stream(c.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .sorted()
                .collect(Collectors.joining(",", "enum[", "]"));
    }

    private static String arguments(final ParameterizedType p, final Deque<Class<?>> open) {
        return Arrays.stream(p.getActualTypeArguments())
                .map(argument -> type(argument, open))
                .collect(Collectors.joining(",", "<", ">"));
    }
}
