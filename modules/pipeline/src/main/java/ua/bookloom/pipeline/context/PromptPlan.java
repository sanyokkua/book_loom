package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;

/**
 * One call's prompt as the context builder planned it.
 *
 * @param blocks the blocks in the order they are sent, system blocks first; copied
 * @param promptTokens the estimated tokens of every block together; zero or more
 * @param maxOutputTokens the most tokens the reply may take; positive
 * @param contextSize the window the call is sized for; positive
 */
public record PromptPlan(List<Block> blocks, int promptTokens, int maxOutputTokens, int contextSize) {

    private static final String BLOCK_SEPARATOR = "\n\n";

    /** Copies the blocks. */
    public PromptPlan {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    /**
     * The system message: the system blocks, a blank line between two.
     *
     * @return never null; empty when the plan has no system block
     */
    public String systemText() {
        return text(Block.Role.SYSTEM);
    }

    /**
     * The last user message: the user blocks, a blank line between two.
     *
     * @return never null; empty when the plan has no user block
     */
    public String userText() {
        return text(Block.Role.USER);
    }

    private String text(final Block.Role role) {
        return String.join(
                BLOCK_SEPARATOR,
                blocks.stream()
                        .filter(block -> block.role() == role)
                        .map(Block::text)
                        .toList());
    }
}
