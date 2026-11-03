package ua.bookloom.llm.knowledge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.SizeClass;

/**
 * Reads the two knowledge files that ship with the app, once. Model knowledge comes from these files only, never from
 * the network; a malformed file is a build defect, so it stops the start rather than degrading quietly.
 */
@Slf4j
@Singleton
public final class ModelKnowledgeFiles {

    static final String QUANT_FILE = "quant-tiers.csv";
    static final String LANGUAGE_FILE = "declared-languages.csv";
    private static final String RESOURCE_DIR = "/ua/bookloom/llm/knowledge/";
    private static final String QUANT_HEADER = "token,family,bpw,needsImatrix,small,mid,large,moe,confidence,source";
    private static final String LANGUAGE_HEADER = "key,matchKind,pattern,listKind,codes,source,evidence";
    private static final Set<String> FAMILIES = Set.of("float", "legacy", "k", "i", "ternary", "mx");
    private static final Set<String> IMATRIX = Set.of("yes", "recommended", "no", "n/a");
    private static final Set<String> CONFIDENCE = Set.of("high", "medium", "low");
    private static final int FIRST_TIER_COLUMN = 4;
    private static final SizeClass[] TIER_COLUMNS = {SizeClass.SMALL, SizeClass.MID, SizeClass.LARGE, SizeClass.MOE};

    private final @Nullable Path quantFile;
    private final @Nullable Path languageFile;
    private final AtomicReference<@Nullable KnowledgeTables> cached = new AtomicReference<>();

    /** Reads the files bundled in this module. */
    @Inject
    public ModelKnowledgeFiles() {
        this.quantFile = null;
        this.languageFile = null;
    }

    /** Reads the given files instead of the bundled ones; tests only. */
    ModelKnowledgeFiles(Path quantFile, Path languageFile) {
        this.quantFile = quantFile;
        this.languageFile = languageFile;
    }

    /**
     * Loads both tables on the first call and returns the same tables afterwards.
     *
     * @return the tables, or {@link ErrorCode#internal} naming the file and the 1-based row of the first defect
     */
    public Result<KnowledgeTables> load() {
        final KnowledgeTables known = cached.get();
        if (known != null) {
            return Result.ok(known);
        }
        log.debug("loading model knowledge from {}", quantFile == null ? "bundled files" : "given files");
        try {
            final KnowledgeTables tables = readTables();
            cached.set(tables);
            log.info(
                    "model knowledge loaded dataVersion={} quantRows={} languageRows={}",
                    tables.dataVersion(),
                    tables.quantRows().size(),
                    tables.languageRows().size());
            return Result.ok(tables);
        } catch (KnowledgeFormatException e) {
            log.error("model knowledge file is malformed: {}", e.getMessage());
            return Result.err(AppError.of(
                    ErrorCode.internal, "Model knowledge is damaged", String.valueOf(e.getMessage()), null, e));
        } catch (IOException | RuntimeException e) {
            log.error("model knowledge could not be read", e);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Model knowledge is missing",
                    "A bundled knowledge file could not be read.",
                    null,
                    e));
        }
    }

    private KnowledgeTables readTables() throws IOException {
        final CsvTable quant = CsvTable.parse(QUANT_FILE, read(quantFile, QUANT_FILE), QUANT_HEADER);
        final CsvTable language = CsvTable.parse(LANGUAGE_FILE, read(languageFile, LANGUAGE_FILE), LANGUAGE_HEADER);
        if (!quant.dataVersion().equals(language.dataVersion())) {
            throw new KnowledgeFormatException(LANGUAGE_FILE, 1, "data-version differs from " + QUANT_FILE);
        }
        return new KnowledgeTables(quant.dataVersion(), quantRows(quant), languageRows(language));
    }

    private static String read(@Nullable Path file, String bundledName) throws IOException {
        if (file != null) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        try (InputStream in = ModelKnowledgeFiles.class.getResourceAsStream(RESOURCE_DIR + bundledName)) {
            if (in == null) {
                throw new IOException("bundled file missing: " + bundledName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, QuantRow> quantRows(CsvTable table) {
        final Map<String, QuantRow> rows = new LinkedHashMap<>();
        for (CsvTable.Row row : table.rows()) {
            final List<String> f = row.fields();
            final QuantRow parsed = new QuantRow(
                    nonEmpty(QUANT_FILE, row, 0),
                    oneOf(row, 1, FAMILIES, QUANT_FILE),
                    number(row, 2),
                    oneOf(row, 3, IMATRIX, QUANT_FILE),
                    tiers(row),
                    oneOf(row, 8, CONFIDENCE, QUANT_FILE),
                    nonEmpty(QUANT_FILE, row, 9));
            if (rows.put(f.get(0), parsed) != null) {
                throw new KnowledgeFormatException(QUANT_FILE, row.number(), "duplicate token " + f.get(0));
            }
        }
        return rows;
    }

    private static Map<SizeClass, QuantTier> tiers(CsvTable.Row row) {
        final Map<SizeClass, QuantTier> tiers = new EnumMap<>(SizeClass.class);
        for (int i = 0; i < TIER_COLUMNS.length; i++) {
            final String cell = row.fields().get(FIRST_TIER_COLUMN + i);
            if (!cell.isEmpty()) {
                tiers.put(TIER_COLUMNS[i], enumValue(QUANT_FILE, row, cell, QuantTier.class));
            }
        }
        return tiers;
    }

    private static List<LanguageRow> languageRows(CsvTable table) {
        final List<LanguageRow> rows = new ArrayList<>();
        final Set<String> keys = new HashSet<>();
        for (CsvTable.Row row : table.rows()) {
            final List<String> f = row.fields();
            if (!keys.add(f.get(0))) {
                throw new KnowledgeFormatException(LANGUAGE_FILE, row.number(), "duplicate key " + f.get(0));
            }
            rows.add(new LanguageRow(
                    nonEmpty(LANGUAGE_FILE, row, 0),
                    enumValue(LANGUAGE_FILE, row, f.get(1), MatchKind.class),
                    nonEmpty(LANGUAGE_FILE, row, 2),
                    enumValue(LANGUAGE_FILE, row, f.get(3), ListKind.class),
                    codes(row),
                    f.get(5),
                    f.get(6)));
        }
        return rows;
    }

    private static Set<String> codes(CsvTable.Row row) {
        final String cell = row.fields().get(4);
        final Set<String> codes = new HashSet<>();
        if (!cell.isEmpty()) {
            for (String code : cell.split(";", -1)) {
                if (!code.matches("[a-z]{2,3}")) {
                    throw new KnowledgeFormatException(LANGUAGE_FILE, row.number(), "bad language code '" + code + "'");
                }
                codes.add(code);
            }
        }
        return codes;
    }

    private static String nonEmpty(String file, CsvTable.Row row, int column) {
        final String value = row.fields().get(column);
        if (value.isEmpty()) {
            throw new KnowledgeFormatException(file, row.number(), "empty column " + (column + 1));
        }
        return value;
    }

    private static String oneOf(CsvTable.Row row, int column, Set<String> allowed, String file) {
        final String value = row.fields().get(column);
        if (!allowed.contains(value)) {
            throw new KnowledgeFormatException(
                    file, row.number(), "unknown value '" + value + "' in column " + (column + 1));
        }
        return value;
    }

    private static double number(CsvTable.Row row, int column) {
        try {
            return Double.parseDouble(row.fields().get(column));
        } catch (NumberFormatException e) {
            throw new KnowledgeFormatException(QUANT_FILE, row.number(), "not a number in column " + (column + 1));
        }
    }

    private static <E extends Enum<E>> E enumValue(String file, CsvTable.Row row, String cell, Class<E> type) {
        try {
            return Enum.valueOf(type, cell);
        } catch (IllegalArgumentException e) {
            throw new KnowledgeFormatException(
                    file, row.number(), "unknown " + type.getSimpleName() + " '" + cell + "'");
        }
    }
}
