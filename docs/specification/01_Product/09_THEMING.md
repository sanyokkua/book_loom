**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`), UX, QA **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/mockups/ui-mockup.html`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md`, `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`

# Theming

This document specifies the token model, palette, and light/dark behaviour. Theming is token-only via looked-up colors
on `.root` (DD-22); no control hard-codes a colour. The mockup `docs/specification/mockups/ui-mockup.html` is the visual
reference. It realizes `FR-UI-05` and `FR-SETTINGS-04`.

The model is **one set of token *roles*, two value blocks**: a single catalogue of role-named looked-up colors (surface,
border, text, primary, status, …) is declared once, and two value blocks — light and dark — supply the hex/rgba for each
role. The active block is **swapped at `.root`** (JavaFX 25 resolves the block matching the OS/selected scheme);
controls never change, only the resolved values do.

## token-model {#token-model}

| ID         | Requirement                                                                                                                                                                                 |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-THEME-1 | All colours are defined as looked-up colors on `.root`; controls reference tokens only.                                                                                                     |
| FR-THEME-2 | Light and dark themes share **one set of token roles**; each role has a light and a dark value, and switching theme swaps the value block at `.root` without per-control overrides.         |
| FR-THEME-3 | JavaFX 25 reads the OS colour scheme (`prefers-color-scheme`); the `system` theme option follows it (FR-SETTINGS-04).                                                                       |
| FR-THEME-4 | The accent token is **fixed to Cognac** in v1; it is not user-selectable (a selectable accent would require the alternate accent values to be enumerated here first, which v1 does not do). |
| FR-THEME-5 | Theme CSS lives in the `:ui` module resources as a single stylesheet declaring the role set once and the two value blocks applied on `.root`.                                               |

## palette {#palette}

Brand palette anchors (theme-independent looked-up colors on `.root`):

| Token role       | Name        | Hex       | Usage                                             |
|------------------|-------------|-----------|---------------------------------------------------|
| Text / dark base | Charcoal    | `#3a4a52` | Text, sidebar/title bar, dark base                |
| Borders / muted  | Slate       | `#b2babd` | Borders, muted text, toggle tracks                |
| Warm surface     | Sand Dollar | `#e7d6c0` | Warm surfaces, selected rows, chips, draft        |
| Primary / accent | Cognac      | `#a58075` | Primary action, brand, accent (fixed, FR-THEME-4) |

## token-catalog {#token-catalog}

The full, normative catalogue of every token role the components reference, with its light and dark value. These are the
only identifiers `:ui` controls may look up; the two value blocks below are swapped at `.root` (FR-THEME-2). Hexes are
the binding values and match `docs/specification/mockups/ui-mockup.html`; any drift between this table and the mockup is
a defect (checked by the palette-token conformance assertion,
`04_Build_and_Release/06_TESTING_STRATEGY.md#ui-conformance`).

**Brand anchors (theme-independent):** `slate` `#b2babd`, `sand` `#e7d6c0`, `cognac` `#a58075`, `charcoal` `#3a4a52`.

| Token role      | Purpose                                   | Light                            | Dark                         |
|-----------------|-------------------------------------------|----------------------------------|------------------------------|
| `bg`            | App background                            | `#f4f1ea`                        | `#283237`                    |
| `surface`       | Primary surface (cards, panes)            | `#ffffff`                        | `#33424a`                    |
| `surface-2`     | Secondary/recessed surface                | `#f6f1e8`                        | `#2e3b41`                    |
| `surface-alt`   | Alternate surface (stripes, wells)        | `#fbf9f4`                        | `#38474f`                    |
| `border`        | Default border                            | `#ddd5c8`                        | `#48585f`                    |
| `border-cool`   | Cool-toned border                         | `#cdd2d3`                        | `#48585f`                    |
| `divider`       | Hairline divider                          | `#eae4d8`                        | `#3c4a51`                    |
| `text`          | Body text                                 | `#2c3941`                        | `#e6ebed`                    |
| `text-strong`   | Emphasised/heading text                   | `#22303a`                        | `#f4f7f8`                    |
| `muted`         | Muted text                                | `#6f7c82`                        | `#9fb0b7`                    |
| `muted-2`       | Second-level muted text                   | `#95928a`                        | `#7f9098`                    |
| `primary`       | Primary action / accent (Cognac)          | `#a58075`                        | `#c4917e`                    |
| `primary-hover` | Primary hover                             | `#916b60`                        | `#d4a593`                    |
| `primary-press` | Primary pressed                           | `#7e5b51`                        | `#b47e6b`                    |
| `primary-fg`    | Foreground on primary                     | `#ffffff`                        | `#241c19`                    |
| `primary-soft`  | Soft primary tint (selected accents)      | `#f0e5dd`                        | `#3f3733`                    |
| `sand-soft`     | Soft sand tint                            | `#f2e9db`                        | `#3a3a37`                    |
| `sand-strong`   | Strong sand                               | `#dcc4a3`                        | `#6d5f4a`                    |
| `selected`      | Selected row/item background              | `#f0e6d6`                        | `rgba(231,214,192,.12)`      |
| `nav-bg`        | Navigation background                     | `#3a4a52`                        | `#20292e`                    |
| `nav-bg-2`      | Navigation secondary background           | `#324148`                        | `#1c2429`                    |
| `nav-fg`        | Navigation foreground                     | `#cdd5d8`                        | `#cdd6da`                    |
| `nav-fg-muted`  | Navigation muted foreground / group label | `#859399`                        | `#7d8f97`                    |
| `nav-active-bg` | Active nav item background                | `rgba(231,214,192,.13)`          | `rgba(231,214,192,.10)`      |
| `nav-active-fg` | Active nav item foreground                | `#f3ede3`                        | `#f3ede3`                    |
| `nav-accent`    | Nav accent                                | `#c99a86`                        | `#c99a86`                    |
| `title-bg`      | Title bar background                      | `#324148`                        | `#1c2429`                    |
| `title-fg`      | Title bar foreground                      | `#dfe4e6`                        | `#dfe4e6`                    |
| `shadow-sm`     | Small elevation shadow                    | `0 1px 2px rgba(58,74,82,.10)`   | `0 1px 2px rgba(0,0,0,.3)`   |
| `shadow`        | Default elevation shadow                  | `0 3px 10px rgba(58,74,82,.12)`  | `0 4px 14px rgba(0,0,0,.35)` |
| `shadow-lg`     | Large elevation shadow                    | `0 12px 34px rgba(35,45,50,.22)` | `0 16px 40px rgba(0,0,0,.5)` |
| `focus`         | Keyboard focus ring                       | `#a58075`                        | `#c4917e`                    |

## status-colours {#status-colours}

Functional status colours (desaturated). Each status has a foreground, a soft background (`-bg`), and a border (`-bd`)
role, used consistently by toasts, banners, and status chips (`11_NOTIFICATIONS_AND_ERRORS.md`):

| Status  | Name       | Role      | Light     | Dark      |
|---------|------------|-----------|-----------|-----------|
| Success | Sage       | `ok`      | `#5f8a6b` | `#7faa8a` |
| Success |            | `ok-bg`   | `#e7efe8` | `#2b3a33` |
| Success |            | `ok-bd`   | `#bcd4c1` | `#3f5a49` |
| Warning | Ochre      | `warn`    | `#bd863a` | `#d0a25a` |
| Warning |            | `warn-bg` | `#f6ecd8` | `#3a3327` |
| Warning |            | `warn-bd` | `#e4cfa2` | `#5c4d31` |
| Danger  | Terracotta | `err`     | `#b0574c` | `#cc7a6f` |
| Danger  |            | `err-bg`  | `#f7e4df` | `#3b2b28` |
| Danger  |            | `err-bd`  | `#e4b6ad` | `#5e3f39` |
| Info    | Slate-blue | `info`    | `#4d6b78` | `#7a9dab` |
| Info    |            | `info-bg` | `#e5edf0` | `#293940` |
| Info    |            | `info-bd` | `#b7cbd2` | `#3d525b` |

## light-and-dark {#light-and-dark}

| ID         | Requirement                                                                                                                                                                                                                                                     |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-THEME-6 | The light theme uses light surfaces with Charcoal text and Cognac/Sand accents.                                                                                                                                                                                 |
| FR-THEME-7 | The dark theme uses a Charcoal base with Cognac and Sand accents.                                                                                                                                                                                               |
| FR-THEME-8 | Both themes derive from the same set of token roles (one role, two values — see `#token-catalog`); each status role has a distinct light and dark value and must retain sufficient contrast in both (see `10_I18N_AND_ACCESSIBILITY.md` contrast requirements). |

## token-naming {#token-naming}

| ID          | Requirement                                                                                                                                                                                                      |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-THEME-9  | Tokens are named by role, not by hue (e.g. `surface`, `text`, `border`, `primary`, `ok`, `warn`, `err`, `info` — see the full `#token-catalog`), so a palette change is a token-value edit within a value block. |
| FR-THEME-10 | Status tokens map to the four functional colours above and are used consistently by toasts, banners, and status indicators (see `08_UI_SCREENS_AND_STATES.md` and `11_NOTIFICATIONS_AND_ERRORS.md`).             |

P6 reference: match the mockup's light and dark renderings for palette, status colours, and accent.
