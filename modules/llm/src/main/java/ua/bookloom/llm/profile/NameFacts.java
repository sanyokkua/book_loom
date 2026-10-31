package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a name says besides who the model is: where it was published, how it is packaged and how its weights are
 * stored. These pick a quant profile; they are not part of the model's identity.
 *
 * @param namespace the publisher or quantiser as written ("unsloth"), or null
 * @param format the weights format ("gguf", "mlx", "gptq"), or null
 * @param quant the first quant token lower-cased without the dynamic prefix ("q4_k_xl"), or null
 * @param quantExtra further quant tokens after the first; never null, may be empty
 * @param dynamic true when the quant had Unsloth's "UD-" prefix
 * @param qat true when the weights were trained for their quant grid
 * @param unquantized true when a QAT repository holds the unquantised weights
 * @param date the YYMM release date in the name ("2507"), or null
 * @param ctx the context suffix ("1m"), or null
 * @param spec speculative-decoding companions such as "mtp" or "dflash"; never null, may be empty
 * @param release a fine-tune's own release ("2.0"), which is not the family version, or null
 * @param shard "part/total" of a split file, or null
 * @param instance the LM Studio loaded-copy number, or null
 * @param unspecified "latest" when the tag is the moving one, or null
 * @param experts the expert count of an "8x7b" name, or null
 * @param expertB the size of one expert in billions, or null
 * @param quantType how the quant is spelt ("kquant", "iquant", "float", "microscaling", ...), or null
 * @param imatrix true when the name says an importance matrix was used or the file is one
 * @param dwq true when the name says distilled weight quantisation
 * @param impliedFormat "mlx" when the publisher only ships MLX but the name does not say it, or null
 * @param sidecar the companion kind of a file ("mmproj", "mtp", "imatrix"), or null
 * @param dateEight an eight-digit date in the name ("20260420"), or null
 * @param monthDay a four-digit month-and-day in the name ("0528"), or null
 * @param finetune a fine-tune's own label ("llamacppfixed"), or null
 * @param finetuner the tuner prefix of the name ("huihui"), or null
 */
public record NameFacts(
        @Nullable String namespace,
        @Nullable String format,
        @Nullable String quant,
        List<String> quantExtra,
        boolean dynamic,
        boolean qat,
        boolean unquantized,
        @Nullable String date,
        @Nullable String ctx,
        List<String> spec,
        @Nullable String release,
        @Nullable String shard,
        @Nullable Integer instance,
        @Nullable String unspecified,
        @Nullable Integer experts,
        @Nullable Double expertB,
        @Nullable String quantType,
        boolean imatrix,
        boolean dwq,
        @Nullable String impliedFormat,
        @Nullable String sidecar,
        @Nullable String dateEight,
        @Nullable String monthDay,
        @Nullable String finetune,
        @Nullable String finetuner) {

    public NameFacts {
        Objects.requireNonNull(quantExtra, "quantExtra");
        Objects.requireNonNull(spec, "spec");
        quantExtra = List.copyOf(quantExtra);
        spec = List.copyOf(spec);
    }
}
