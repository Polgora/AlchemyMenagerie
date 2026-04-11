# AlchemyMenagerie UI Style Guide

Derived exclusively from `ArcanoscopeUI.ui` and `ElementSlot.ui`.
Use this as the reference when building new mod UI pages.

---

## 1. Design Philosophy

- **Deep blue-grey panels** sit against the dark game world — not pure black, not neutral grey.
- **Gold** (`#c9a050`) is the single interactive accent colour: hover states, selected item borders,
  and value labels all use it.
- **Green** (`#60d080`) signals a ready or confirm action. Dim it with `#404848` when the action
  is not yet available.
- **Purple/violet** is reserved for arcane or elemental content: element row labels and contextual
  hint text.
- Text has two tiers: **dim labels** (`#6a7a8a`) name a section; **bright values** (`#c0d0e0`,
  `#c9a050`) show state or selection.

---

## 2. Color Palette

### Button / Panel Backgrounds

| Role                             | Hex       |
|----------------------------------|-----------|
| Stepper arrow / flask icon slot  | `#1a2530` |
| Element row button               | `#1a2840` |
| Confirm / harvest button         | `#1a3a20` |

### Hover States

| Role                           | Hex       |
|--------------------------------|-----------|
| Stepper arrow / flask slot     | `#c9a050` ← gold |
| Element row                    | `#2a4870` |
| Confirm / harvest button       | `#2a6a38` |

### Pressed States

| Role                           | Hex       |
|--------------------------------|-----------|
| Gold-hover button (stepper, flask) | `#a08040` |
| Element row                    | `#122038` |
| Confirm / harvest button       | `#1a4828` |

### Text Colors

| Role                               | Hex       | Size |
|------------------------------------|-----------|------|
| Page title (via DefaultLabelStyle) | framework |  —   |
| Section heading                    | `#6a7a8a` | 16   |
| Stepper description label          | `#6a7a8a` | 14   |
| Stepper value label (bold)         | `#c9a050` | 16   |
| Stepper arrow labels               | `#c0d0e0` | 16   |
| Element row labels                 | `#c890f0` | 16   |
| Flask fill percentage labels       | `#90a0b0` | 13   |
| Detail header (bold)               | `#c9a050` | 16   |
| Detail body text                   | `#90a0b0` | 14   |
| Help / hint text                   | `#a080c0` | 16   |
| Confirm label (active, bold)       | `#60d080` | 16   |
| Confirm label (inactive)           | `#404848` | 16   |

### Selection Highlight

| State      | Applied to                   | Hex       |
|------------|------------------------------|-----------|
| Selected   | Highlight wrapper background | `#c9a050` |
| Unselected | Highlight wrapper background | `#00000000` (transparent) |

---

## 3. Typography

| Use                              | Size | Bold |
|----------------------------------|------|------|
| Page title                       | DefaultLabelStyle | yes |
| Section heading                  | 16   | no   |
| Help / hint text                 | 16   | no   |
| Stepper description label        | 14   | no   |
| Stepper value label              | 16   | yes  |
| Stepper arrow labels (`<` / `>`) | 16   | no   |
| Element row label                | 16   | no   |
| Flask fill label                 | 13   | no   |
| Detail header                    | 16   | yes  |
| Detail body                      | 14   | no   |
| Confirm button label             | 16   | yes  |

**`Wrap: true`** — apply to any label in a fixed-width container where overflow is possible:
detail body text, help text, any label inside a narrow column.

**`HitTestVisible: false`** — required on every label nested inside a button. Without it the
label captures the click and the button never fires.

---

## 4. Layout Conventions

### File Skeleton

```
$C = "Common.ui";

$C.@PageOverlay {}

$C.@Container {
    Anchor: (Width: W, Height: H);

    #Title {
        Label #TitleLabel {
            Text: "";
            Style: (...$C.@DefaultLabelStyle, HorizontalAlignment: Center,
                    VerticalAlignment: Center, RenderBold: true);
        }
    }

    #Content {
        Group {
            LayoutMode: Top;

            // sections...
        }
    }
}
```

Note: the Arcanoscope page does **not** use `$C.@BackButton {}`. The page closes via Confirm
or the built-in Esc dismiss (`CustomPageLifetime.CanDismiss`).

### Layout Modes

| Mode            | When to use                                         |
|-----------------|-----------------------------------------------------|
| `Top`           | Stacking sections vertically inside `#Content`      |
| `CenterMiddle`  | Centering a single row (button, stepper, icon row)  |
| `Left`          | Building horizontal rows (stepper, icon strip)      |

### Container Size

| UI complexity                  | Width | Height |
|--------------------------------|-------|--------|
| Multi-section with icon slots  | 420   | 580    |

Adjust height as sections are added or removed; keep width at 420 unless a section
genuinely needs more horizontal space.

### Spacing and Padding

- `Anchor: (Top: 10, Bottom: 6)` on the first label in `#Content`.
- `Anchor: (Top: 0, Bottom: 8)` on section heading labels.
- `Padding: (Horizontal: 20)` on text labels (detail header, detail body, help text)
  so they don't touch the panel edges.
- `Padding: (Horizontal: 12)` inside button labels (element rows).
- `Padding: 2` inside highlight wrapper groups.
- Horizontal spacers between items: `Group { Anchor: (Width: 6); }` (tight),
  `Group { Anchor: (Width: 10); }` (normal), `Group { Anchor: (Width: 16); }` (loose / between icons).
- `$C.@ContentSeparator { Anchor: (Top: 8–10, Bottom: 8–10); }` between every major section.

---

## 5. Component Patterns

### Stepper Row

A description label followed by `<` button, value label, `>` button — all in a `LayoutMode: Left`
row, centred by a `LayoutMode: CenterMiddle` wrapper.

```
Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 32, Bottom: 10);

    Group {
        LayoutMode: Left;

        Label {
            Text: "Harvest Power";
            Anchor: (Height: 32);
            Style: (FontSize: 14, TextColor: #6a7a8a, VerticalAlignment: Center);
        }

        Group { Anchor: (Width: 10); }

        Button #PowerDecreaseButton {
            Anchor: (Width: 28, Height: 28);
            Style: (Default: (Background: #1a2530),
                    Hovered: (Background: #c9a050), Pressed: (Background: #a08040));
            Label {
                Anchor: (Full: 0); HitTestVisible: false;
                Text: "<";
                Style: (FontSize: 16, TextColor: #c0d0e0,
                        HorizontalAlignment: Center, VerticalAlignment: Center);
            }
        }

        Group { Anchor: (Width: 6); }

        Label #PowerValueLabel {
            Anchor: (Width: 52, Height: 32);
            Text: "50%";
            Style: (FontSize: 16, TextColor: #c9a050,
                    HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true);
        }

        Group { Anchor: (Width: 6); }

        Button #PowerIncreaseButton {
            Anchor: (Width: 28, Height: 28);
            Style: (Default: (Background: #1a2530),
                    Hovered: (Background: #c9a050), Pressed: (Background: #a08040));
            Label {
                Anchor: (Full: 0); HitTestVisible: false;
                Text: ">";
                Style: (FontSize: 16, TextColor: #c0d0e0,
                        HorizontalAlignment: Center, VerticalAlignment: Center);
            }
        }
    }
}
```

Java: update `#PowerValueLabel.Text` on every change. Clamp in logic; do not disable
the buttons visually — a click past the boundary is simply ignored.

### Dynamically-Appended List Row (ElementSlot.ui pattern)

When a list is built at runtime via `cmd.clear` / `cmd.append`, define the row in its own `.ui` file:

```
// ElementSlot.ui
Group {
    Anchor: (Width: 300, Height: 36);

    Group #ElementHighlight {
        Anchor: (Full: 0);
        Background: #00000000;
        Padding: 2;

        ItemSlotButton #ElementButton {
            Anchor: (Full: 0);
            Style: (Default: (Background: #1a2840),
                    Hovered: (Background: #2a4870), Pressed: (Background: #122038));

            Label #ElementLabel {
                Anchor: (Full: 0);
                Padding: (Horizontal: 12);
                HitTestVisible: false;
                Style: (FontSize: 16, TextColor: #c890f0,
                        HorizontalAlignment: Start, VerticalAlignment: Center);
                Text: "";
            }
        }
    }
}
```

The parent list group uses `LayoutMode: Top` with `Padding: (Horizontal: 20)`:

```
Group #ElementList {
    LayoutMode: Top;
    Padding: (Horizontal: 20);
}
```

Java:
```java
cmd.clear("#ElementList");
for (int i = 0; i < elements.size(); i++) {
    cmd.append("#ElementList", "ElementSlot.ui");
    cmd.set("#ElementList[" + i + "] #ElementLabel.Text", elements.get(i));
    cmd.set("#ElementList[" + i + "] #ElementHighlight.Background",
            selected ? "#c9a050" : "#00000000");
    evt.addEventBinding(CustomUIEventBindingType.Activating,
            "#ElementList[" + i + "] #ElementButton",
            EventData.of("Action", "select").append("Element", elements.get(i)));
}
```

### Icon Slot Strip (Flask row pattern)

A horizontal strip of fixed-size icon slots, each with a fill/status label below.
Each slot tile is a `LayoutMode: Top` group containing the highlight wrapper and the label.

```
Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 96);

    Group {
        LayoutMode: Left;

        // --- Slot 0 ---
        Group {
            LayoutMode: Top;
            Anchor: (Width: 68);

            Group #MyHighlight0 {
                Anchor: (Width: 68, Height: 68);
                Background: #00000000;
                Padding: 2;

                ItemSlotButton #MyButton0 {
                    Anchor: (Full: 0);
                    Style: (Default: (Background: #1a2530),
                            Hovered: (Background: #c9a050), Pressed: (Background: #a08040));

                    Group {
                        Anchor: (Full: 0); Padding: 2; HitTestVisible: false;
                        Group {
                            Anchor: (Full: 0);
                            Background: "Common/BlockSelectorSlotBackground.png";
                            HitTestVisible: false;
                            ItemSlot #MyIcon0 {
                                Anchor: (Full: 0);
                                ShowQualityBackground: true;
                                HitTestVisible: false;
                            }
                        }
                    }
                }
            }

            Label #MyFill0 {
                Anchor: (Height: 20, Top: 4);
                Style: (FontSize: 13, TextColor: #90a0b0, HorizontalAlignment: Center);
                Text: "";
            }
        }

        Group { Anchor: (Width: 16); }   // spacer between slots

        // --- Slot 1, Slot 2... (same structure, increment IDs) ---
    }
}
```

Outer group height = icon height (68) + label height (20) + label top offset (4) + padding (2×2)
= ~96.  Adjust `Anchor: (Height: ...)` on the `LayoutMode: CenterMiddle` wrapper accordingly.

### Help / Hint Text

```
Label #HelpText {
    Text: "";
    Anchor: (Top: 10, Bottom: 6);
    Padding: (Horizontal: 20);
    Style: (FontSize: 16, TextColor: #a080c0, HorizontalAlignment: Center);
}
```

Update from Java to reflect the current step:
- Step not started: guide the player to act.
- Step in progress: clarify or confirm.
- Error / empty state: explain why nothing appears.

### Detail Panel (header + body)

```
Label #DetailHeader {
    Text: "";
    Anchor: (Bottom: 6);
    Padding: (Horizontal: 20);
    Style: (FontSize: 16, TextColor: #c9a050, HorizontalAlignment: Center, RenderBold: true);
}

Label #DetailBody {
    Text: "";
    Anchor: (Bottom: 12);
    Padding: (Horizontal: 20);
    Style: (FontSize: 14, TextColor: #90a0b0, HorizontalAlignment: Center, Wrap: true);
}
```

### Confirm Button

```
Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 44, Bottom: 8);

    ItemSlotButton #ConfirmButton {
        Anchor: (Width: 220, Height: 38);
        Style: (Default: (Background: #1a3a20),
                Hovered: (Background: #2a6a38), Pressed: (Background: #1a4828));
        Label #ConfirmLabel {
            Anchor: (Full: 0);
            HitTestVisible: false;
            Style: (FontSize: 16, TextColor: #60d080,
                    HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true);
            Text: "Confirm";
        }
    }
}
```

Java: set `#ConfirmLabel.Style.TextColor` to `#404848` when not yet ready,
`#60d080` when ready.

---

## 6. Section Separator

```
$C.@ContentSeparator {
    Anchor: (Top: 8, Bottom: 8);   // adjust spacing as needed
}
```

Use between every major section. Tighten to `Top: 0, Bottom: 8` when a heading
label immediately follows.

---

## 7. Java Wiring Reference

```java
// build() — load file and populate from scratch
cmd.append("MyUI.ui");
populateAll(cmd, evt);

// incremental update (same UI file, state change only)
UICommandBuilder cmd = new UICommandBuilder();
UIEventBuilder   evt = new UIEventBuilder();
populateAll(cmd, evt);
sendUpdate(cmd, evt, false);
```

- Re-bind **all** event bindings on every update, not just on build.
- `cmd.clear("#ElementList")` before re-appending dynamic rows.
- `cmd.set("#Element.Property", value)` — the property path mirrors the `.ui` tree:
  `#ElementList[0] #ElementLabel.Text`, `#MyHighlight0.Background`, etc.
- Action strings use `snake_case`: `"power_down"`, `"select"`, `"select_flask"`, `"confirm"`.
- Pass extra data with `EventData.of("Action", "select").append("Element", elementString)`.

---

## 8. Encoding

- **ASCII only** in any string set from Java (`cmd.set`). Unicode characters such as
  arrows (`←`, `→`) may not be in the game font and render as `?`.
- `<` and `>` hardcoded in a `.ui` file render correctly.
  When set from Java, they also render correctly.
- Bold, italic, and other markup are controlled via `RenderBold` and `Style` properties,
  not inline text tags.

---

## 9. Naming Conventions

| Element                     | ID pattern                   | Example                     |
|-----------------------------|------------------------------|-----------------------------|
| Page title label            | `#PascalCaseLabel`           | `#BlockNameLabel`           |
| Help / hint label           | `#HelpText`                  | —                           |
| Stepper decrease button     | `#NameDecreaseButton`        | `#PowerDecreaseButton`      |
| Stepper increase button     | `#NameIncreaseButton`        | `#PowerIncreaseButton`      |
| Stepper value label         | `#NameValueLabel`            | `#PowerValueLabel`          |
| Dynamic list container      | `#NameList`                  | `#ElementList`              |
| Selection highlight (index) | `#PrefixHighlightN`          | `#ArcFlaskHighlight0`       |
| Icon slot button (index)    | `#PrefixButtonN`             | `#ArcFlaskButton0`          |
| Icon slot item (index)      | `#PrefixIconN`               | `#ArcFlaskIcon0`            |
| Fill / status label (index) | `#PrefixFillN`               | `#ArcFlaskFill0`            |
| Section label               | `#NameLabel` or anonymous    | `#FlaskSectionLabel`        |
| Detail header               | `#NameDetailHeader`          | `#FlaskDetailHeader`        |
| Detail body                 | `#NameDetailContents`        | `#FlaskDetailContents`      |
| Confirm button              | `#ConfirmButton`             | —                           |
| Confirm label               | `#ConfirmLabel`              | —                           |