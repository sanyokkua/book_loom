package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;

/**
 * The model that drafts a retry book before the stage under test runs: for every source paragraph it answers the draft
 * the case file plants, defects included, whatever call asks (batch, single draft or repair). The real job then decides
 * those drafts with its own gates, so the stored records, statuses and context snapshots are the ones a run leaves.
 */
final class PlantedDrafts implements ChatModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*?)</s>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("<Text>\\n(.*)\\n</Text>", Pattern.DOTALL);

    private final Map<String, String> draftBySource;
    private final List<String> sourcesLongestFirst;

    PlantedDrafts(final Map<String, String> draftBySource) {
        this.draftBySource = Map.copyOf(draftBySource);
        this.sourcesLongestFirst = draftBySource.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        final String user = request.messages().get(1).content();
        final Matcher items = ITEM.matcher(user);
        final List<Map<String, String>> pairs = new ArrayList<>();
        while (items.find()) {
            pairs.add(Map.of("id", items.group(1), "target", draftFor(items.group(2))));
        }
        if (!pairs.isEmpty()) {
            return reply(Map.of("items", pairs));
        }
        final Matcher text = TEXT.matcher(user);
        return reply(Map.of("target", draftFor(text.find() ? text.group(1) : user)));
    }

    private String draftFor(final String shown) {
        return sourcesLongestFirst.stream()
                .filter(shown::contains)
                .findFirst()
                .map(draftBySource::get)
                .orElse(shown);
    }

    private static Result<ChatResponse> reply(final Map<String, Object> body) {
        try {
            return Result.ok(new ChatResponse(MAPPER.writeValueAsString(body), FinishReason.STOP));
        } catch (JsonProcessingException e) {
            throw new AssertionError("planted reply", e);
        }
    }
}
