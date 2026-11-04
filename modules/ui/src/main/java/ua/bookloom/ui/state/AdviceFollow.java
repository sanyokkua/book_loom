package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.project.BookBrief;

/**
 * Builds the value a line follows: the reading for a chosen model and the open project's language pair. Working the
 * value out asks the reader for the reading, which is read once and idempotent, so a line that is shown always has its
 * reading on the way.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdviceFollow {

    /**
     * The reading to show.
     *
     * @param reader where readings come from
     * @param current the open project, whose brief holds the language pair
     * @param chosen the model to describe, or empty for none; read on the FX thread
     * @param inputs what the choice and the pair depend on
     * @return a binding holding the reading, or null while there is none; FX thread only
     */
    public static ObjectBinding<@Nullable AdviceReading> of(
            final AdviceReader reader,
            final CurrentProject current,
            final Supplier<Optional<ModelSelection>> chosen,
            final Observable... inputs) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(chosen, "chosen");
        final Observable[] watched = new Observable[inputs.length + 2];
        System.arraycopy(inputs, 0, watched, 0, inputs.length);
        watched[inputs.length] = current.brief();
        watched[inputs.length + 1] = reader.readings();
        return Bindings.createObjectBinding(
                () -> {
                    final Optional<ModelSelection> selection = chosen.get();
                    if (selection.isEmpty() || selection.get().modelId().isBlank()) {
                        return null;
                    }
                    final BookBrief brief = current.brief().get();
                    final String source = brief == null ? null : brief.sourceLanguage();
                    final String target = brief == null ? null : brief.targetLanguage();
                    reader.request(selection.get(), source, target);
                    return reader.readingFor(selection.get(), source, target);
                },
                watched);
    }
}
