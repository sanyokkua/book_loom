# Task 1.2 — Document Lifetime, Format Dispatch, and Port Diagnostics

## Summary

Implement only OpenSpec task 1.2 from [`tasks.md`](../../../openspec/changes/add-translation-engine-and-cli/tasks.md), leaving task 1.1 unchanged.

The implementation will:

- Add `Result<Boolean> close(Document document)` to `DocumentPort`.
- Add registry-backed `close(String documentId)` methods to all four readers.
- Dispatch close operations through `DocumentService`, with typed internal-error handling.
- Replace duplicated suffix checks in `FormatResolver` with `BookFormat.ofFileName(...)` and `matchedSuffix(...)`.
- Correct `DocumentPort` Javadoc.
- Add DEBUG/TRACE diagnostics for every document-port call.
- Add test-only Logback output and regenerate the document lockfile.

## Implementation Steps

1. **Prepare isolated execution**

   - Leave Plan Mode before mutating files.
   - Materialize this plan as `docs/superpowers/plans/2026-09-14-task-1-2-document-close.md`.
   - Use the worktree skill to create `feature/add-translation-engine-and-cli--1.2-document-close` from the current clean parent branch.
   - Record the base SHA (`8f124d0` currently).
   - Create the SDD ledger and generate a task brief containing only OpenSpec task 1.2.

2. **Delegate implementation**

   - Spawn one clean-context implementer with `fork_context: false`, using an explicit standard coding model and medium reasoning effort.
   - The implementer reads the generated brief first, follows TDD, makes no subagent calls, commits its changes, and writes a detailed report with RED/GREEN evidence to the SDD workspace.
   - The controller does not implement or fix production code directly.

3. **Write the failing document tests first**

   Modify `modules/document/src/test/java/ua/bookloom/document/DocumentServiceTest.java` with real `DocumentService` instances from `DocumentServices.newService()`:

   - Open and close a TXT document: first close returns successful `true`; second close returns successful `false`.
   - Open and close an EPUB document: close returns successful `true`.
   - Open and close an FB2 document: close returns successful `true`.
   - Open and close a Markdown document: close returns successful `true`.
   - Close an opened Markdown document, then write it to `Book.uk.md`: return `ErrorCode.internal` and leave the destination absent.

   Run the focused test command before implementation and record the expected compile/test failure as TDD RED evidence.

4. **Implement the close contract and reader release methods**

   - In `DocumentPort.java`, add:

     ```java
     Result<Boolean> close(Document document);
     ```

   - In each reader — `EpubReader`, `Fb2Reader`, `MarkdownReader`, and `TxtReader` — add `public boolean close(String documentId)` that validates the id and returns whether `registry.close(documentId).isPresent()`.
   - Do not change `OpenDocumentRegistry`; its existing removal operation is the backing implementation.
   - Keep readers free of new logging; `DocumentService` remains the module boundary and logging owner.

5. **Extend `DocumentService`**

   - Add `close(Document)` with `Objects.requireNonNull` at the public boundary.
   - Switch on `document.format()` and call the corresponding reader’s close method.
   - Wrap unexpected throwables as `Result.err` with `ErrorCode.internal`, preserving the cause and logging the ERROR exactly once through the existing `internalError(...)` path.
   - Add parameterized DEBUG outcome logging for success/false/error.
   - Add DEBUG entry/outcome logging to existing `open`, `write`, and `unmask` methods without moving or duplicating existing WARN/ERROR construction logs.
   - Required diagnostic data:
     - `open`: source path and resolved format; success or error code.
     - `write`: document id, format, destination, target language, translated-segment count; success or error code.
     - `unmask`: format, segment id, placeholder count; success or error code.
     - `close`: document id and format; successful open-state result or error code.
     - `unmask` TRACE: masked input and restored text.
   - Keep book text exclusively at TRACE and use parameterized SLF4J messages.

6. **Refactor `FormatResolver`**

   - Resolve the filename with `BookFormat.ofFileName(source.getFileName().toString())`.
   - Preserve the existing validation failure for unsupported extensions.
   - Use `matchedSuffix(...)` or the format’s suffix metadata to distinguish `.fb2.zip` from bare `.fb2`.
   - Preserve all existing content checks:
     - EPUB and zipped FB2 require ZIP magic.
     - Bare FB2 requires a FictionBook root.
     - Markdown and TXT remain content-unconfirmed.
     - Non-regular files remain refused before parsing.
   - Remove duplicated hard-coded suffix matching and any now-unused imports/helpers.

7. **Correct documentation and test logging**

   - In `DocumentPort` Javadoc:
     - Replace “Both methods” with “Every method”.
     - Describe corrupt and DRM refusals as `ErrorCode.validation`.
     - Explain that JPMS exports and `ports-not-concretes` keep callers on the port instead of claiming `:pipeline` cannot depend on `:document`.
   - In `modules/document/build.gradle.kts`, add `testRuntimeOnly(libs.logback.classic)`.
   - Create `modules/document/src/test/resources/logback-test.xml` with `build/test-logs/test.log` as the file destination, the same human-readable pattern as current application logging, `ua.bookloom` at `${BOOKLOOM_LOG_LEVEL:-DEBUG}`, all other loggers at `WARN`, and no console appender.
   - Run `rtk ./gradlew resolveAndLockAll --write-locks` and verify only the required document lock state changes.

## Verification and Review

- Run focused RED, then GREEN tests for `DocumentServiceTest`.
- Run `:document` tests including `FormatDispatchTest` and the golden round-trip suite.
- Run `rtk ./gradlew spotlessApply`.
- Run `rtk ./gradlew :document:build`.
- Run:

  ```bash
  BOOKLOOM_LOG_LEVEL=TRACE rtk ./gradlew :document:test \
    --tests 'ua.bookloom.document.DocumentServiceTest' --rerun
  ```

- Inspect `modules/document/build/test-logs/test.log` for DEBUG entry/outcome lines and TRACE unmask lines; run the focused unmask test if needed to exercise that path.
- Run the project Definition-of-Done gate:

  ```bash
  rtk ./gradlew clean build check spotlessCheck
  ```

- Generate the review package from the recorded base SHA and dispatch an independent clean-context task reviewer with the task brief, implementer report, and diff package.
- Fix Critical/Important findings only through the implementer, using the prescribed resume/re-review loop.
- After review is clean, mark task 1.2 `[x]`, update the SDD ledger, verify OpenSpec status reports `2/13` complete, and squash-merge the task branch into `feature/add-translation-engine-and-cli`.

## Assumptions

- `BookFormat` suffix APIs and document record copy methods from task 1.1 are already complete and will not be modified.
- `DocumentService` is the only `DocumentPort` implementation.
- No general specification or project-document updates belong to task 1.2.
- The specified document-port tests are the user-visible lifetime evidence; no JavaFX app run is required for this document-only task.
- Implementation remains paused until this Plan Mode session is replaced by execution mode.
