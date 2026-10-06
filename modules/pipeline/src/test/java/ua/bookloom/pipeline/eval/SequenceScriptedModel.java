package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;

/**
 * A model that translates the fixture word for word with a fixed Ukrainian vocabulary and drifts on purpose, so the
 * metric code is proven against defects whose counts are known: {@code master} and {@code Mr} change rendering and
 * {@code Bartimaeus} changes spelling halfway through the book, one narrator paragraph says «я була», one paragraph is
 * echoed in English and one carries a stray quote. The drift depends on the order a source was first seen, so a repeated
 * call for the same text answers the same way.
 */
final class SequenceScriptedModel implements ChatModel {

    /** Sources first seen at or after this position drift. */
    static final int DRIFT_AT = 160;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*?)</s>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("<Text>\\n(.*)\\n</Text>", Pattern.DOTALL);
    private static final Pattern PIECE = Pattern.compile("⟦[^⟧]*⟧|\\p{L}[\\p{L}'’]*");
    private static final List<String> FILLER = List.of("тихо", "двері", "дощ", "свічка", "вікно", "ранок", "камінь");
    static final String ECHO_SOURCE = "The morning brought no rain and no peace.";
    static final String STRAY_SOURCE = "Do not trouble yourself";

    private final Map<String, Integer> firstSeen = new LinkedHashMap<>();
    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    private int filler;

    /** Every request the job sent, in order. */
    List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        requests.add(request);
        final String user = request.messages().get(1).content();
        final Matcher text = TEXT.matcher(user);
        final List<String> entries = new ArrayList<>();
        if (text.find()) {
            return reply(Map.of("target", translate(text.group(1))));
        }
        final Matcher items = ITEM.matcher(user);
        while (items.find()) {
            entries.add(items.group(1));
            entries.add(translate(items.group(2)));
        }
        return reply(Map.of("items", pairs(entries)));
    }

    private static List<Map<String, String>> pairs(final List<String> flat) {
        final List<Map<String, String>> pairs = new ArrayList<>();
        for (int i = 0; i < flat.size(); i += 2) {
            pairs.add(Map.of("id", flat.get(i), "target", flat.get(i + 1)));
        }
        return pairs;
    }

    private static Result<ChatResponse> reply(final Map<String, Object> body) {
        try {
            return Result.ok(new ChatResponse(MAPPER.writeValueAsString(body), FinishReason.STOP));
        } catch (JsonProcessingException e) {
            throw new AssertionError("scripted reply", e);
        }
    }

    private String translate(final String source) {
        final int position = firstSeen.computeIfAbsent(source, key -> firstSeen.size());
        final boolean drifted = position >= DRIFT_AT;
        if (source.contains(ECHO_SOURCE)) {
            return source;
        }
        final StringBuilder out = new StringBuilder();
        final Matcher piece = PIECE.matcher(source);
        while (piece.find()) {
            piece.appendReplacement(out, Matcher.quoteReplacement(word(piece.group(), drifted)));
        }
        piece.appendTail(out);
        return source.contains(STRAY_SOURCE) ? out + "\"" : out.toString();
    }

    private String word(final String word, final boolean drifted) {
        if (word.startsWith("⟦")) {
            return word;
        }
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "mr" -> drifted ? "пан" : "містер";
            case "mrs" -> "місіс";
            case "ms" -> "міс";
            case "master" -> drifted ? "учитель" : "господар";
            case "imp", "imps" -> "біс";
            case "magician" -> "маг";
            case "boy" -> "хлопчик";
            case "sir" -> "сер";
            case "pentacle" -> "пентакль";
            case "circle" -> "коло";
            case "bartimaeus" -> drifted ? "Бартімеус" : "Бартімей";
            case "nathaniel" -> "Натаніель";
            case "underwood" -> "Андервуд";
            case "lovelace" -> "Лавлейс";
            case "i" -> "я";
            case "laughed" -> "була";
            default -> nextFiller();
        };
    }

    private String nextFiller() {
        filler = (filler + 1) % FILLER.size();
        return FILLER.get(filler);
    }
}
