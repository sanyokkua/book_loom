package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Finds quant tokens ("Q4_K_M", "UD-Q4_K_XL", "4bit", "nvfp4") in a name; shared by the parser and the normaliser. */
@Slf4j
final class QuantScanner {

    private static final String DYNAMIC_PREFIX = "ud-";

    QuantScan scan(final String text) {
        Objects.requireNonNull(text, "text");
        final String low = text.toLowerCase(Locale.ROOT);
        final List<String> found = new ArrayList<>();
        String label = null;
        for (final QuantTokens.Span span : QuantTokens.find(low)) {
            if (label == null) {
                label = text.substring(span.start(), span.end());
            }
            found.add(low.substring(span.start(), span.end()));
        }
        if (found.isEmpty()) {
            return new QuantScan(null, null, false, List.of(), low);
        }
        final String first = found.getFirst();
        final List<String> extra =
                found.stream().skip(1).map(QuantScanner::normal).toList();
        final String rest = QuantTokens.replaceAll(low, "-");
        log.debug("Quant scan text={} first={} extra={}", text, first, extra);
        return new QuantScan(normal(first), label, first.startsWith(DYNAMIC_PREFIX), extra, rest);
    }

    /** Reads a folder such as "BF16" that holds the quant the file name leaves out. */
    QuantScan scanFolder(final String folder, final QuantScan named) {
        final String low = folder.toLowerCase(Locale.ROOT);
        if (!QuantTokens.isToken(low)) {
            return named;
        }
        log.debug("Quant from folder {}", folder);
        return new QuantScan(normal(low), folder, low.startsWith(DYNAMIC_PREFIX), List.of(), named.rest());
    }

    private static String normal(final String quant) {
        final String bare = quant.startsWith(DYNAMIC_PREFIX) ? quant.substring(DYNAMIC_PREFIX.length()) : quant;
        return switch (bare) {
            case "q8" -> "q8_0";
            case "fp16" -> "f16";
            case "fp32" -> "f32";
            default -> bare;
        };
    }
}
