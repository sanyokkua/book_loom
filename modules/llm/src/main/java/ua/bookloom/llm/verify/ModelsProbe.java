package ua.bookloom.llm.verify;

import java.util.List;
import ua.bookloom.api.llm.StageOutcome;

/** The models stage's verdict with the ids the server listed, for the test dialog's response column. */
record ModelsProbe(StageOutcome outcome, List<String> ids) {}
