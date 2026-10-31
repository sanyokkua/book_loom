package ua.bookloom.llm.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;

/**
 * Notices a reply under an answer schema whose translation was cut at a straight quote mark: with Gemma 3 on Ollama the
 * schema's grammar can end a JSON string at the first {@code "} the model writes, so a sentence with dialogue comes
 * back as valid JSON that stops just before the quotation. The cut leaves a translation ending on a space, a colon, a
 * comma or a dash, where no finished sentence ends, while its own source item holds a straight quote mark with text
 * after it. Each translation is paired with its source item by id ({@code <s id="…">}), or, for a reply with one
 * translation, with the request's last {@code <Text>} or {@code <Source>} block; the prompt's own text around the items
 * (its schema line, a folded system message) is never read as a source.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuoteCut {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TARGET = "target";
    private static final String ID = "id";
    // A batch item as the batch prompt writes it; the id is the one the reply answers with.
    private static final Pattern ITEM = Pattern.compile("<s id=\"([^\"]*)\">(.*?)</s>", Pattern.DOTALL);
    // The one text of a single-item call; the system text's examples come first, so the last block is the source.
    private static final Pattern BLOCK = Pattern.compile("<(Text|Source)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern OPENING_QUOTE = Pattern.compile("\"\\S");
    private static final Pattern CUT_END = Pattern.compile("[\\s:,\\u2014\\u2013-]\\z");

    /** The source items of one request: by id, and the single source a reply with no id answers. */
    private record Sources(
            Map<String, String> byId, @Nullable String single) {}

    /**
     * Whether a reply's translation stops where its own source opens a quotation.
     *
     * @param request the request the reply answered; non-null
     * @param reply the reply's scrubbed text; non-null
     * @return {@code true} if the request asked for a schema and a {@code target} of the reply ends where no sentence
     *     ends while its own source item opens a straight quotation, {@code false} otherwise
     */
    public static boolean isCut(final ChatRequest request, final String reply) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reply, "reply");
        if (request.responseFormat() == null) {
            return false;
        }
        final Sources sources = sources(request.messages());
        final List<JsonNode> answers = answers(reply);
        final long cut =
                answers.stream().filter(answer -> isCutAnswer(answer, sources)).count();
        log.debug(
                "Quote cut check items={} singleSource={} targets={} cut={}",
                sources.byId().size(),
                sources.single() != null,
                answers.size(),
                cut);
        return cut > 0;
    }

    private static boolean isCutAnswer(final JsonNode answer, final Sources sources) {
        final String target = answer.path(TARGET).asText();
        if (!CUT_END.matcher(target).find()) {
            return false;
        }
        final JsonNode id = answer.path(ID);
        final @Nullable String source = id.isTextual() ? sources.byId().get(id.asText()) : sources.single();
        final boolean quoted = source != null && OPENING_QUOTE.matcher(source).find();
        log.debug("Quote cut candidate id={} sourceFound={} sourceQuoted={}", id.asText(""), source != null, quoted);
        return quoted;
    }

    private static Sources sources(final List<ChatMessage> messages) {
        final Map<String, String> byId = new LinkedHashMap<>();
        String lastBlock = null;
        for (final ChatMessage message : messages) {
            if (message.role() != ChatRole.USER) {
                continue;
            }
            final Matcher item = ITEM.matcher(message.content());
            while (item.find()) {
                byId.put(item.group(1), item.group(2));
            }
            final Matcher block = BLOCK.matcher(message.content());
            while (block.find()) {
                lastBlock = block.group(2);
            }
        }
        final @Nullable String single = lastBlock != null
                ? lastBlock
                : byId.size() == 1 ? byId.values().iterator().next() : null;
        return new Sources(byId, single);
    }

    private static List<JsonNode> answers(final String reply) {
        try {
            final JsonNode root = JSON.readTree(reply);
            return root == null
                    ? List.of()
                    : root.findParents(TARGET).stream()
                            .filter(node -> node.path(TARGET).isTextual())
                            .filter(node -> !node.path(TARGET).asText().isBlank())
                            .toList();
        } catch (JsonProcessingException notJson) {
            log.debug("Quote cut check skipped: the reply is not JSON");
            return List.of();
        }
    }
}
