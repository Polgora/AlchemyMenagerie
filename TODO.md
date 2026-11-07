# TODO

Backlog identified during in-game testing on 2026-11-07 (post Server 0.7.6 API migration).

1. **Missing `_Known` variants for some vanilla blocks.** Found during Arcanoscope testing — some vanilla blocks have no `_Known` counterpart, so they can never be scanned/journaled. Audit which vanilla `BlockType`/`Item` IDs under `Server/Item/Items/` lack a `_Known` JSON and add them, following the existing `_Known` convention (`KnownItemSwapper`, `JournalManager.isScannableItem`).

2. **Add a close ("X") button to UI menus.** Custom UI pages (Journal, Arcanoscope, Menagerie Table/Nest, Anchor Info, Runebound Whistle, etc.) need a consistent close affordance, per the UI Style Guide conventions in `CLAUDE.md`.

3. **Redesign the Discoveries tab UI.** Replace the current layout with a simple clickable list of plain item names (e.g. "Dirt") that open a sub-page showing that item's detail info (element composition / description), rather than the current inline display. Needs its own design pass against `UI_STYLE_GUIDE.md` and the Discoveries page width (640, per CLAUDE.md's Standard Container Widths table).

4. **Add a UI button/shortcut to open the Menagerie Journal** — currently only accessible via the physical book item; add another access point (e.g. a hotkey or keybind-triggered UI shortcut) so players don't have to carry/find the book.

5. **Lower the movement margin when scanning with the Arcanoscope.** User wants the look-direction tolerance that cancels a scan on movement (`LOOK_THRESHOLD` in `ArcanoscopePacketWatcher`) reduced.

6. **Arcanoscope scan particles should not activate if the targeted block can't be scanned.** Currently the particle effect fires regardless of whether the block is actually eligible (e.g. not in `JournalManager.isScannableItem`), which is misleading — particles should only play once the target is confirmed scannable.

7. **Add loot items that help players discover bestiary/creature recipes.**

8. **Goggles to see the elements of scanned materials.**

9. **Droppers to remove all of one essence from a flask.**

10. **All elements in UI use periodic table of elements icons.**
