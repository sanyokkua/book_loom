# 15d overnight log

| Task | Evidence | Notes |
|---|---|---|
| 15d.2 | `:pipeline` full-module check green; e4b fixture run, 4 formats: only flags are true English leftovers confirmed by `validate-translated-book.py` (epub ch4-p1, txt:38); the one length false positive (txt:79) did not recur after widening the compact-line exemption to 8 words | footnote-marker spacing deferred to 15d.3; span text logged at TRACE not WARN |
| 15d.3 | `:pipeline` full-module check green; e4b fixture, 4 formats: 0 straight in-word apostrophes and 0 `...` in uk output; flags unchanged (the two true English leftovers) | footnote-marker spacing deferred (markers are indistinguishable tokens); `ui:SmoothScrollTest` flaked under load, passes alone |
| 15d.4 | api/util/document/pipeline/ui full-module check green; fixture scan proposes only the 8 real names + intended terms; Bartimaeus 1 first chapter and whole book: none of T-shirt, Yellow Pages, Hyperion Books, author name survive (CROYDON not in this edition) | built in memory not via make-fixtures.py; FB2 has only notes/comments back matter; "Magic Mirror TM" survives; stale NullAway on `:pipeline:compileJava` clears with --rerun; visual check of the new Check column/dialog left for the morning |
