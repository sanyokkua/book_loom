package ua.bookloom.persistence.sqlite;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the stored {@code :api} records as JSON documents and reads them back equal.
 *
 * <p>The mapper never calls {@code setAccessible}: {@code :api} exports its packages without opening them, and a
 * record's public canonical constructor and accessors are reachable as they are. No mix-ins are needed today because
 * no stored record holds a sealed or polymorphic type; add one here (never an annotation in {@code :api}) when one
 * does.
 *
 * <p>Only the record components are stored: other getters (a derived {@code isSuggested()}, a {@code names()}
 * helper) are switched off, so a derived view is never written and cannot break a read.
 *
 * <p>Unknown properties fail on read: a stored document that no longer matches its record is drift to be seen, not
 * hidden. Before the first release a database with another schema number is replaced anyway (ADR-0043), so no record
 * needs leniency.
 *
 * <p>Failures are unchecked ({@link IllegalStateException} with the Jackson cause); the adapters wrap them into an
 * {@code AppError} at their boundary.
 */
@Slf4j
public final class JsonCodec {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(paths())
            .visibility(new RecordComponentsOnly())
            .disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
            .disable(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Serialises a stored value to a JSON document.
     *
     * @param value a non-null record or collection of records
     * @return the JSON document
     * @throws IllegalStateException when the value cannot be serialised
     */
    public String write(final Object value) {
        Objects.requireNonNull(value, "value");
        try {
            final String json = mapper.writeValueAsString(value);
            log.debug("json write type={} length={}", value.getClass().getSimpleName(), json.length());
            if (log.isTraceEnabled()) {
                log.trace("json write document {}", json);
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot write " + value.getClass().getSimpleName(), e);
        }
    }

    /**
     * Reads a stored JSON document back into its type.
     *
     * @param json a document written by {@link #write(Object)}
     * @param type the record type to read
     * @return the value; never null
     * @throws IllegalStateException when the document does not match the type
     */
    public <T> T read(final String json, final Class<T> type) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(type, "type");
        log.debug("json read type={} length={}", type.getSimpleName(), json.length());
        if (log.isTraceEnabled()) {
            log.trace("json read document {}", json);
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot read " + type.getSimpleName(), e);
        }
    }

    /**
     * Stores a path as it was written. Jackson's default writes a {@code file:} URI, which makes a relative path
     * absolute against the working folder on read, so the value would not come back equal.
     */
    private static SimpleModule paths() {
        final SimpleModule module = new SimpleModule("paths");
        module.addSerializer(Path.class, ToStringSerializer.instance);
        module.addDeserializer(Path.class, new PathDeserializer());
        return module;
    }

    /** Reads a path from the text {@link #paths()} wrote. */
    private static final class PathDeserializer extends FromStringDeserializer<Path> {

        private static final long serialVersionUID = 1L;

        PathDeserializer() {
            super(Path.class);
        }

        @Override
        protected Path _deserialize(final String value, final DeserializationContext context) {
            return Path.of(value);
        }
    }

    /** Of a record's methods only the component accessors are properties; any other type keeps Jackson's rules. */
    private static final class RecordComponentsOnly extends VisibilityChecker.Std {

        private static final long serialVersionUID = 1L;

        RecordComponentsOnly() {
            super(
                    Visibility.PUBLIC_ONLY,
                    Visibility.PUBLIC_ONLY,
                    Visibility.ANY,
                    Visibility.ANY,
                    Visibility.PUBLIC_ONLY);
        }

        @Override
        public boolean isGetterVisible(final AnnotatedMethod method) {
            final Class<?> owner = method.getDeclaringClass();
            return owner.isRecord() ? isComponent(owner, method) : super.isGetterVisible(method);
        }

        @Override
        public boolean isIsGetterVisible(final AnnotatedMethod method) {
            final Class<?> owner = method.getDeclaringClass();
            return owner.isRecord() ? isComponent(owner, method) : super.isIsGetterVisible(method);
        }

        private static boolean isComponent(final Class<?> owner, final AnnotatedMethod method) {
            return Arrays.stream(owner.getRecordComponents())
                    .anyMatch(component -> component.getName().equals(method.getName()));
        }
    }
}
