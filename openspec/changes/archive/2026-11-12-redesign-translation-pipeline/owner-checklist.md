# Owner checklist for `redesign-translation-pipeline` (task 17.4)

Everything below needs a person. The change is built, gated and hand-run (17.1 to 17.3); the archive waits for these answers. Mark each item OK, or write what to change.

## A. Words to confirm (English and Ukrainian)

**Owner answer (2026-11-12): wording is fine, items 1 to 9 as written (the recommended pull tag stays `gemma4:26b-a4b`).**

1. **Privacy subtitle, Settings header** (`settings.subtitle`)
   - EN: Your books stay private when you use a local model. BookLoom sends your text only to the AI model you choose here.
   - UK: Ваші книжки залишаються приватними, коли ви користуєтеся локальною моделлю. BookLoom надсилає ваш текст лише моделі ШІ, яку ви оберете тут.
2. **Empty Projects screen title / tagline** (`projects.empty.title`)
   - EN: Translate a book. Private when you use a local AI model.
   - UK: Перекладіть книгу. Приватно, коли ви користуєтеся локальною моделлю ШІ.
3. **Privacy line for a loopback (local) provider** (`settings.providers.privacy.local`)
   - EN: Your book stays private when you use a local model.
   - UK: Ваша книжка залишається приватною, коли ви користуєтеся локальною моделлю.
   - For a provider outside this computer the line adds: "With a provider outside this computer, the text you translate is sent to {host}."
4. **Memory line, Projects / Start / Models** (`model.recommend.line`; the pull command is text only, never run)
   - EN: This computer has {GB} GB: {model} is recommended. Pull it with `ollama pull {tag}`.
   - UK: На цьому комп’ютері {GB} ГБ пам’яті: рекомендовано {model}. Завантажте командою `ollama pull {tag}`.
   - The backticks are literal in both languages; confirm you want them.
5. **Sampling choices, Models tab**: EN `Profile default` / `Custom`; UK `Як у профілі` / `Власні`.
6. **Thinking row for a model that always thinks**
   - With a level: EN "Always thinks; BookLoom sends the lowest level, {level}." / UK "Міркує завжди; BookLoom надсилає найнижчий рівень, {level}."
   - Fixed: EN "Always thinks; this cannot be changed." / UK "Міркує завжди; це не змінити."
7. **Ollama-native mismatch message** (a provider saved as Ollama but another server answers)
   - EN: The server at this address is not Ollama. / The server at this address is {engine}, not Ollama.
   - UK: Сервер за цією адресою — не Ollama. / Сервер за цією адресою — {engine}, а не Ollama.
8. **Generation time-limit lines, Settings > Generation** (EN / UK):
   - Time limits / Обмеження часу; fixed / незмінні
   - Names, brief, file name, summary: {n} s (longer for a model that always thinks) / Імена, бриф, назва файлу, підсумок: {n} с (довше для моделі, яка завжди міркує)
   - Other calls: {x} s per expected token, at least the provider's request timeout, at most {n} s / Інші запити: {x} с на кожен очікуваний токен, щонайменше тайм-аут запиту провайдера, щонайбільше {n} с
   - Silence while streaming / Тиша під час потокової відповіді; Tries: up to {n} tries; a slow answer is asked again once, shorter / до {n} спроб; повільну відповідь запитують ще раз, коротшу
   - The per-call "Took 0:01 · limit 3:00" line (UK "Тривав … · межа …") is on the progress detail.
9. **Recommended pull tag.** The recommendation uses `gemma4:e4b` under 24 GB, `gemma4:26b-a4b` from 24 GB, and offers `gemma4:31b` from 36 GB as optional. Your own tag for the 26b model is `gemma4:26b-mlx`. Decide whether the recommendation should name `26b-mlx` for Apple machines instead of `26b-a4b`.

## B. Reading real output

**Owner answer (2026-11-12): books read; there is room for improvement, accepted for now.**

10. **Bartimaeus 1, en to uk** (outputs in `.temporary_context/redesign-closing/book-en-uk/A2-e4b-balanced/exports` and `B-26b-balanced/exports`, Balanced, whole book). Read some chapters of both and say whether the quality is acceptable. Counts that need your eye: the 26b output has 1,125 Latin words and 22 language-identity findings, against 305 and 5 on e4b; 44 flagged segments (26b) against 29 (e4b).
11. **Strugatsky 1977, ru to uk** (new: `ru-uk-new/`, old pipeline: `ru-uk-79ffede6/`). Read the new output next to the old one. Known: 21 of 100 sampled paragraphs of the new output have some meaning error, 5 of 100 keep a Russian content word, and one paragraph (segment 773) was written in Russian.

## C. Decisions and notes

**Owner answer (2026-11-12): accepted. The archive is run by the owner in a separate session.**

12. **Eight database resets noticed.** Until the first release the database has no migrations: when the schema or the stored record shapes change, an old `bookloom.db` is set aside as `bookloom.db.old-<time>` and a new one is made (ADR-0043). Confirm this stays acceptable until release.
13. **Known limits accepted on 2026-11-11** (written in `docs/evaluation/results-redesign-closing-run.md#redesign-closing-run`, `AGENTS.md` and backlog rows D20 to D23): quality not proven better than the old pipeline (Q1); narrator gender slips (9 at Balanced and 5 at Max against 0 before); Russian content words left in ru to uk output (R8); possessives and case forms stored as separate glossary rows (R3, R4, hand-run D7). No follow-up change is planned.
14. **Seen in the hand run, not fixed, your call** (backlog rows D24, D25):
    - On the small invented books a chapter summary is often not stored ("Chapter summary not kept … reason=all-dropped"), so each Continue asks for it again. Look at a real book to see whether it happens there.
    - The Ukrainian UI was not hand-checked (the OS locale was English).
    - The finished banner says "1 segment needs your review" when only a second-look item is left.
    - With "gemma4" typed in the model filter the open drop-down showed 1 of 5 rows; it may be cut off at the window bottom.
    - The hand-run agent's scripted click on a Quit dialog landed after the app had closed, at screen point (1014, 363), on whatever window was underneath; check that nothing odd happened.
15. **After your answers:** run `/opsx:archive`, then the workflow follow-up listed at the end of `tasks.md` (rename stale reviewer-era scenario titles, rewrite three stale Purpose paragraphs, split the long legacy requirements), then start the next change from `docs/spec_baseline/implementation_plan/CHANGE_BACKLOG.md`.
