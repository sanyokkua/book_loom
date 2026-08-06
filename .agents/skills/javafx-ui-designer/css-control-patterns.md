# CSS & Control Patterns

Supporting reference for `javafx-ui-designer/SKILL.md`. Everything here is expressed in terms of BookLoom's
**existing** fixed palette and token catalogue (`docs/specification/01_Product/09_THEMING.md#token-catalog`) and the
AtlantaFX base theme it sits on top of — no new token role is introduced by this file. Token references below use the
`-color-<role>` naming already used in `theming-tokens.md` and `SKILL.md` (e.g. `-color-primary`, `-color-surface`);
the exact CSS custom-property names are fixed when the `:ui` stylesheet is authored, but the *roles* are the
catalogue's and are not invented here.

## Layout-pane selection guide

| Need | Pane |
|---|---|
| A vertical/horizontal stack (toolbar, form section) | `VBox` / `HBox` |
| The app-window root (nav + title bar + content) | `BorderPane` |
| A structured form (label/field grid) | `GridPane` |
| An overlay (modal content, empty-state over a list) | `StackPane` |
| A wrapping or uniform grid (chip list, thumbnail grid) | `FlowPane` / `TilePane` |
| Pixel-pinned positioning | `AnchorPane` — **use sparingly**; pixel-pinned layouts don't adapt to resizing |

Grow-priority idioms: `HBox.setHgrow(node, Priority.ALWAYS)` / `VBox.setVgrow(node, Priority.ALWAYS)` to let one child
absorb extra space. The FXML spacer idiom pushes a control to the far end of a toolbar without a grow-priority call on
the control itself:

```xml
<HBox>
    <Button text="Back"/>
    <Region HBox.hgrow="ALWAYS"/>
    <Button text="Save" styleClass="button, primary"/>
</HBox>
```

## The JavaFX `Region` box model

Painting order, outside in: **background → background-insets → border → padding → content.** Unlike CSS-on-the-web,
`-fx-background-insets` shrinks the background *inward* from the border box before the border is painted — a common
source of "why is there a gap between my border and my fill" confusion. `-fx-padding` then reserves space for content
inside the border.

## CSS property reference by node level

| Level | Representative properties |
|---|---|
| `Node` | `-fx-opacity`, `-fx-cursor`, `-fx-effect`, `-fx-rotate`, `-fx-scale-x`/`-y` |
| `Region` | `-fx-background-color`, `-fx-background-insets`, `-fx-background-radius`, `-fx-border-color`, `-fx-border-width`, `-fx-border-radius`, `-fx-padding`, `-fx-min/pref/max-width`/`-height` |
| `Labeled` | `-fx-text-fill`, `-fx-font-size`, `-fx-font-weight`, `-fx-graphic-text-gap`, `-fx-content-display` |
| `TextInputControl` | `-fx-prompt-text-fill`, `-fx-highlight-fill`, `-fx-highlight-text-fill` |
| Layout-pane-specific | `-fx-spacing` (`VBox`/`HBox`), `-fx-hgap`/`-fx-vgap` (`GridPane`/`FlowPane`), `-fx-alignment` |

## Pseudo-class reference

| Pseudo-class | Fires when |
|---|---|
| `:hover` | pointer is over the node |
| `:focused` | node has keyboard focus |
| `:pressed` | mouse/touch is currently down on the node |
| `:disabled` | `Node.disable` is true |
| `:selected` | a `Toggle`/row/tab is the selected one |
| `:armed` | a `ButtonBase` is pressed and the pointer is still inside it (the moment before it fires) |
| `:empty` / `:filled` | a `ListCell`/`TableCell`/`TreeCell` has no item bound / has an item bound |
| `:showing` | a `Menu`/popup/`ComboBox` dropdown is currently open |

## `derive()` / `ladder()` for state colors

Compute a hover/pressed shade *from* an existing token instead of hard-coding a second hex for every interactive
state:

```css
.button.primary {
    -fx-background-color: -color-primary;
}
.button.primary:hover {
    -fx-background-color: derive(-color-primary, -10%);
}
.button.primary:pressed {
    -fx-background-color: derive(-color-primary, -20%);
}
```

`ladder()` picks whichever of a list of candidate colors has the best contrast against a base color — useful for a
text-fill that must stay legible against a background token that differs between light and dark:

```css
-fx-text-fill: ladder(-color-primary, -color-text-strong 50%, white 50%);
```

Prefer `derive()`/`ladder()` over a second literal hex per state: the state color moves automatically if the base
token's value ever changes in `09_THEMING.md`.

## Layered-background depth trick

`-fx-background-color`, `-fx-background-insets`, and `-fx-background-radius` all accept **comma-separated lists**,
painted back-to-front. Stacking two backgrounds — one slightly larger, offset, and darker — fakes a border or a soft
shadow using existing surface/border tokens, and is cheaper than a real effect:

```css
.card {
    -fx-background-color: -color-border, -color-surface;
    -fx-background-insets: 0, 1;
    -fx-background-radius: 6, 5;
}
```

This paints a 1px `-color-border` ring under a `-color-surface` fill inset by 1px — a border-like edge with zero
`-fx-border-*` properties and no `-fx-effect`.

## Per-control recipes (BookLoom's actual tokens)

**Button color-role variants** via style-class composition, not a size variant (a `large`/`small` class would
reintroduce the density concept `theming-tokens.md` already defers for v1 — don't add one):

```xml
<Button text="Save" styleClass="button, primary"/>
<Button text="Delete project" styleClass="button, danger"/>
<Button text="Cancel" styleClass="button, ghost"/>
```

```css
.button.primary { -fx-background-color: -color-primary; -fx-text-fill: -color-primary-fg; }
.button.danger  { -fx-background-color: -color-err;     -fx-text-fill: white; }
.button.ghost   { -fx-background-color: transparent;    -fx-text-fill: -color-text; }
```

**TextField error state** — toggle a style class from the controller, never `setStyle`:

```java
if (validationFailed) {
    textField.getStyleClass().add("error");
} else {
    textField.getStyleClass().remove("error");
}
```

```css
.text-field.error { -fx-border-color: -color-err; -fx-border-width: 1.5; }
```

**TableView conditional row styling** via a row factory toggling style classes in `updateItem()` — and per the
logging rule in `logging.md`, never log inside this same `updateItem()` override, since it runs on every cell/row
recycle:

```java
setRowFactory(tv -> new TableRow<>() {
    @Override
    protected void updateItem(SegmentRow item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().remove("row-flagged");
        if (!empty && item != null && item.isFlagged()) {
            getStyleClass().add("row-flagged");
        }
    }
});
```

**Thin scrollbar** recipe (transparent viewport, rounded thumb, hidden increment/decrement buttons):

```css
.thin-scroll-pane, .thin-scroll-pane .viewport {
    -fx-background-color: transparent;
}
.thin-scroll-pane .scroll-bar:vertical .track,
.thin-scroll-pane .scroll-bar:vertical .increment-button,
.thin-scroll-pane .scroll-bar:vertical .decrement-button {
    -fx-background-color: transparent;
    -fx-pref-width: 0;
}
.thin-scroll-pane .scroll-bar:vertical .thumb {
    -fx-background-color: -color-muted;
    -fx-background-radius: 4;
    -fx-background-insets: 0 2 0 2;
}
```

## Anti-patterns

- **Overriding `-fx-base` on a control.** AtlantaFX derives a large family of colors from `-fx-base`; overriding it
  cascades unpredictably through controls you didn't intend to touch. Target the specific property (`-fx-background-
  color`, `-fx-text-fill`, …) instead.
- **`-fx-effect: dropshadow(...)` for routine elevation.** Real drop-shadow effects are comparatively expensive to
  render and repaint. Reserve them for dialogs/popovers that genuinely float above content; use the layered-
  background trick above for cards/borders in normal layout flow.
- **Fighting AtlantaFX's base control theme piecemeal.** Patching one control's native look at a time produces
  visible inconsistency across the app. BookLoom already commits to token-only overrides layered on top of AtlantaFX
  (`theming-tokens.md`) — stay consistent with that rather than hand-tuning individual controls' internals.

## Explicitly excluded (do not add later without a spec/ADR change)

- An 8px spacing/density token scale — `theming-tokens.md` already states a SHOULD to add **no** density/spacing
  tokens for v1.
- A typography-scale token set (`-fx-font-size-xs`…`3xl`) or a border-radius token scale — both would add token
  roles beyond the frozen `09_THEMING.md#token-catalog`, which needs a spec/ADR decision, not a skill file, to
  introduce.
