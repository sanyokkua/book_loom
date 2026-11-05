package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.pipeline.PromptSection;

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

    /**
     * The blocks as the parts a person reads on "What the model saw": a block whose first line is a bracketed heading
     * keeps it as the part's heading.
     *
     * @return never null; one part per block, in the order sent
     */
    public List<PromptSection> sections() {
        return sectionsOf(blocks);
    }

    /**
     * Blocks as the parts a person reads, as {@link #sections()} words them; the run's fixed frame is stored this way.
     *
     * @param blocks the non-null blocks, in the order sent
     * @return never null; one part per block
     */
    public static List<PromptSection> sectionsOf(final List<Block> blocks) {
        return blocks.stream().map(PromptPlan::section).toList();
    }

    private static PromptSection section(final Block block) {
        final List<String> lines = block.text().lines().toList();
        final boolean headed = lines.getFirst().startsWith("[");
        return new PromptSection(
                block.slot(),
                headed ? lines.getFirst() : "",
                block.role() == Block.Role.SYSTEM ? PromptSection.Origin.SYSTEM : PromptSection.Origin.USER,
                headed ? lines.subList(1, lines.size()) : lines);
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
