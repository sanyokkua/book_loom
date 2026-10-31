package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a name came from, as the paste and the path around the id said it.
 *
 * @param inputForm the shape of the paste ("url:hf:file", "cmd:ollama run", "display-name"), or null for a bare id
 * @param quantizer the quantising publisher in lower case ("unsloth"), or null
 * @param org the vendor in an {@code Org_Model} repository name ("qwen" in {@code Qwen_Qwen3.5-9B}), or null
 * @param repo the repository {@code org/name} the paste named, or null
 * @param file the model file name with its extension, or null
 * @param subfolder the folder that held the file or that the link opened, or null
 * @param revision a branch or commit other than main, or null
 * @param routerVariants router suffixes such as "free"; never null, may be empty
 * @param serverAlias the {@code --alias} a server command gave, or null
 * @param alias "latest" for an OpenRouter alias id, or null
 */
public record NameOrigin(
        @Nullable String inputForm,
        @Nullable String quantizer,
        @Nullable String org,
        @Nullable String repo,
        @Nullable String file,
        @Nullable String subfolder,
        @Nullable String revision,
        List<String> routerVariants,
        @Nullable String serverAlias,
        @Nullable String alias) {

    public NameOrigin {
        Objects.requireNonNull(routerVariants, "routerVariants");
        routerVariants = List.copyOf(routerVariants);
    }
}
