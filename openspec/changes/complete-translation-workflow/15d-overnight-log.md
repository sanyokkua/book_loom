# 15d overnight log

| Task | Evidence | Notes |
|---|---|---|
| 15d.2 | `:pipeline` full-module check green; e4b fixture run, 4 formats: only flags are true English leftovers confirmed by `validate-translated-book.py` (epub ch4-p1, txt:38); the one length false positive (txt:79) did not recur after widening the compact-line exemption to 8 words | footnote-marker spacing deferred to 15d.3; span text logged at TRACE not WARN |
| 15d.3 | `:pipeline` full-module check green; e4b fixture, 4 formats: 0 straight in-word apostrophes and 0 `...` in uk output; flags unchanged (the two true English leftovers) | footnote-marker spacing deferred (markers are indistinguishable tokens); `ui:SmoothScrollTest` flaked under load, passes alone |
