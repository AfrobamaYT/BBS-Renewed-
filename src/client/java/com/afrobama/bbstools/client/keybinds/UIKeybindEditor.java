// Coded by Afrobama/Claude — in-game keybind editor (preset-aware, undo-integrated)
package com.afrobama.bbstools.client.keybinds;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import com.afrobama.bbstools.client.keybinds.KeybindOverrides;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preset-aware in-game editor for {@link KeyCombo} bindings. Top row:
 * preset selector with {@code +} (new), rename, delete buttons. Below
 * that: search filter + reset-all. Below that: scrollable list of
 * keybinds grouped by category, each row click-to-rebind.
 */
public class UIKeybindEditor extends UIOverlayPanel
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/keybind-editor");

    public final UIScrollView list;
    public final UIScrollView presetRow;
    public final UITextbox search;
    public final UIButton resetAll;
    public final UIButton newPreset;
    public final UIButton deletePreset;
    public final UILabel openHint;

    private String filter = "";

    public UIKeybindEditor()
    {
        super(IKey.constant("Keybind Editor"));

        this.resizable();

        /* === Preset selector row === */
        this.presetRow = new UIScrollView();
        this.presetRow.row(2).preferred(0);
        this.presetRow.relative(this.content).x(6).y(2).w(1F, -12).h(20);

        /* === Search + reset === */
        this.search = new UITextbox(64, this::setFilter);
        this.search.placeholder(IKey.constant("Filter…"));

        this.newPreset = new UIButton(IKey.constant("+ Preset"), (b) ->
        {
            String before = KeybindOverrides.getActivePreset();
            String name = "Custom " + (KeybindOverrides.getPresetNames().size());
            KeybindOverrides.createPreset(name);
            KeybindOverrides.setActivePreset(name);
            pushPresetSwitchUndo(before, name);
            this.rebuildAll();
        });
        this.newPreset.tooltip(IKey.constant("Create a new empty preset and switch to it"));

        this.deletePreset = new UIButton(IKey.constant("× Delete"), (b) ->
        {
            KeybindOverrides.deletePreset(KeybindOverrides.getActivePreset());
            this.rebuildAll();
        });
        this.deletePreset.tooltip(IKey.constant("Delete the current preset (BBS Default cannot be deleted)"));

        this.resetAll = new UIButton(IKey.constant("↺ Reset"), (b) ->
        {
            KeybindOverrides.resetAll();
            this.rebuild();
        });
        this.resetAll.tooltip(IKey.constant("Reset all keybinds in the current preset to defaults"));

        UIElement controlRow = UI.row(4, this.search, this.newPreset, this.deletePreset, this.resetAll);
        controlRow.relative(this.content).x(6).y(24).w(1F, -12).h(20);

        /* === Open-shortcut hint === */
        this.openHint = UI.label(IKey.constant(""));
        this.openHint.relative(this.content).x(6).y(46).w(1F, -12).h(12);

        /* === Keybind list === */
        this.list = new UIScrollView();
        this.list.relative(this.content).x(0).y(60).w(1F).h(1F, -60);
        this.list.column(2).vertical().stretch().scroll().padding(6);

        this.content.add(this.presetRow, controlRow, this.openHint, this.list);

        this.rebuildAll();
    }

    private void refreshOpenHint()
    {
        String viewer = Keys.KEYBINDS == null ? "?" : Keys.KEYBINDS.getKeyCombo();
        /* The editor-open keybind lives under BBSToolsKeys, not as a BBS-Keys patch
         * (the old fork patched Keys.KEYBIND_EDITOR — clean rebuild leaves BBS-Keys untouched). */
        String editor = BBSToolsKeys.KEYBIND_EDITOR_OPEN == null ? "?" : BBSToolsKeys.KEYBIND_EDITOR_OPEN.getKeyCombo();
        this.openHint.label = IKey.constant("§7Open shortcuts: §f" + viewer + " §7Viewer  §8| §f" + editor + " §7Editor");
    }

    private void setFilter(String value)
    {
        this.filter = value == null ? "" : value.toLowerCase();
        this.rebuild();
    }

    private void rebuildAll()
    {
        this.refreshOpenHint();
        this.rebuildPresetRow();
        this.rebuild();
    }

    private void rebuildPresetRow()
    {
        this.presetRow.removeAll();

        String active = KeybindOverrides.getActivePreset();
        for (String name : KeybindOverrides.getPresetNames())
        {
            UIButton btn = new UIButton(IKey.constant(name), (b) ->
            {
                String before = KeybindOverrides.getActivePreset();
                KeybindOverrides.setActivePreset(name);
                pushPresetSwitchUndo(before, name);
                this.rebuildAll();
            });
            btn.h(18);
            if (name.equals(active))
            {
                btn.tooltip(IKey.constant("§a(active) §7click to re-apply"));
            }
            this.presetRow.add(btn);
        }

        this.presetRow.resize();
    }

    private static void pushPresetSwitchUndo(String before, String after)
    {
        if (before == null || before.equals(after)) return;
        final String b = before;
        final String a = after;
        com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack.push(
            new com.afrobama.bbstools.client.ui.undo.BBSToolsUndoEntry(
                "Switch preset " + b + " → " + a,
                () -> { try { KeybindOverrides.setActivePreset(b); }
                        catch (Throwable t) { LOG.warn("undo preset switch failed", t); } },
                () -> { try { KeybindOverrides.setActivePreset(a); }
                        catch (Throwable t) { LOG.warn("redo preset switch failed", t); } }));
    }

    private void rebuild()
    {
        this.list.removeAll();

        Map<String, UIElement> groups = new LinkedHashMap<>();
        String f = this.filter;

        for (KeyCombo combo : KeybindOverrides.all().values())
        {
            if (!f.isEmpty())
            {
                String label = combo.label == null ? combo.id : combo.label.get().toLowerCase();
                String id = combo.id == null ? "" : combo.id.toLowerCase();
                if (!label.contains(f) && !id.contains(f)) continue;
            }

            String cat = combo.categoryKey == null ? "all" : combo.categoryKey;
            UIElement group = groups.computeIfAbsent(cat, (k) ->
            {
                UIElement g = new UIElement();
                g.column(2).vertical().stretch();

                UILabel header = UI.label(IKey.constant("§7" + capitalize(k).toUpperCase()));
                g.add(header.marginTop(4));

                return g;
            });

            group.add(this.buildRow(combo));
        }

        for (UIElement g : groups.values())
        {
            this.list.add(g);
        }

        if (groups.isEmpty())
        {
            this.list.add(UI.label(IKey.constant("§7No keybinds match filter.")).marginTop(20));
        }

        this.list.resize();
    }

    private UIElement buildRow(KeyCombo combo)
    {
        java.util.List<KeyCombo> conflicts = KeybindOverrides.findConflicts(combo);
        boolean hasConflict = !conflicts.isEmpty();

        /* Red §c prefix when this combo's keys collide with another
         *  combo's keys. The user can hover the row to see WHICH
         *  combos are conflicting. */
        String name = combo.label == null ? combo.id : combo.label.get();
        UILabel label = UI.label(IKey.constant(hasConflict ? "§c" + name : name));
        label.h(20);

        if (hasConflict)
        {
            StringBuilder tip = new StringBuilder("§cKey conflict with: §f");
            for (int i = 0; i < conflicts.size(); i++)
            {
                if (i > 0) tip.append(", ");
                KeyCombo c = conflicts.get(i);
                tip.append(c.label == null ? c.id : c.label.get());
            }
            label.tooltip(IKey.constant(tip.toString()));
        }

        UIKeybind editor = new UIKeybind((newCombo) ->
        {
            int[] before = snapshotKeys(combo);
            combo.keys.clear();
            combo.keys.addAll(newCombo.keys);
            KeybindOverrides.onComboEdited(combo);
            int[] after = snapshotKeys(combo);
            pushKeybindUndo("Rebind " + comboLabel(combo), combo, before, after);
            /* Rebuild the entire list so the red highlight reflects
             *  the new state — both this row AND the row that just
             *  stopped conflicting (or just started). */
            this.rebuild();
        });

        /* mouse() schaltet das aufnehmen von maustasten frei. Ohne den aufruf ignoriert
         * UIKeybind jeden klick, der nicht die aufnahme startet — deshalb liessen sich hier
         * Mouse4/Mouse5 nicht belegen, obwohl der ganze unterbau sie laengst kann: KeyCombo
         * speichert maustasten als NEGATIVE ids und prueft sie ueber
         * Window.isMouseButtonPressed(-key), und KeyCodes.getName(-3) liefert "Mouse 4".
         * BBS' eigener keybind-eintrag in den settings macht es genauso (UIValueMap:259).
         *
         * escape() wird BEWUSST nicht gesetzt: damit bleibt ESC das abbrechen einer laufenden
         * aufnahme. In den settings ist es gesetzt, dort ist ESC belegbar — hier waere ein
         * verklickt-tippen sonst nicht mehr zurueckzunehmen. */
        editor.mouse();
        editor.setKeyCombo(combo);
        editor.w(120);

        UIButton reset = new UIButton(IKey.constant("↺"), (b) ->
        {
            int[] before = snapshotKeys(combo);
            KeybindOverrides.resetToDefault(combo);
            int[] after = snapshotKeys(combo);
            pushKeybindUndo("Reset " + comboLabel(combo) + " to default", combo, before, after);
            this.rebuild();
        });
        reset.w(20).h(20);
        reset.tooltip(IKey.constant("Reset this keybind to default"));

        /* Das zweite Aufnahmefeld. null = Feature ausgeschaltet; dann sieht die Zeile exakt so
         * aus wie vorher, ohne leeren Platzhalter. */
        UIKeybind zweit = UIZweitbelegungFeld.bauen(combo, this::rebuild);

        UIElement row = zweit == null
            ? UI.row(4, label, editor, reset)
            : UI.row(4, label, editor, zweit, reset);

        row.h(20);
        return row;
    }

    private static String capitalize(String s)
    {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* === Undo-stack integration (Phase 9b) === */

    private static int[] snapshotKeys(KeyCombo combo)
    {
        int[] arr = new int[combo.keys.size()];
        for (int i = 0; i < combo.keys.size(); i++) arr[i] = combo.keys.get(i);
        return arr;
    }

    private static String comboLabel(KeyCombo combo)
    {
        if (combo.label != null)
        {
            try { return combo.label.get(); }
            catch (Throwable t) { LOG.warn("combo.label resolve failed", t); }
        }
        return combo.id == null ? "?" : combo.id;
    }

    /** Push an undo entry that restores the keybind to its pre-edit
     *  state. We bypass {@link KeybindOverrides#onComboEdited(KeyCombo)}'s
     *  default-vs-custom branching on undo by always calling it — the
     *  method's safeguard for unknown defaults means our restored keys
     *  end up correctly persisted either way. */
    private static void pushKeybindUndo(String label, KeyCombo combo, int[] before, int[] after)
    {
        if (java.util.Arrays.equals(before, after)) return;
        com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack.push(
            new com.afrobama.bbstools.client.ui.undo.BBSToolsUndoEntry(
                label,
                () -> applyAndPersist(combo, before),
                () -> applyAndPersist(combo, after)));
    }

    /** M3 fix from the final review — snapshot keys before mutating
     *  so that a throw inside {@link KeybindOverrides#onComboEdited}
     *  leaves the live combo at the pre-edit state instead of a
     *  half-cleared list. */
    private static void applyAndPersist(KeyCombo combo, int[] keys)
    {
        int[] rollback = snapshotKeys(combo);
        try
        {
            combo.keys.clear();
            for (int k : keys) combo.keys.add(k);
            KeybindOverrides.onComboEdited(combo);
        }
        catch (Throwable t)
        {
            LOG.warn("applyAndPersist for combo '{}' failed — rolling back",
                combo == null ? "?" : combo.id, t);
            try
            {
                combo.keys.clear();
                for (int k : rollback) combo.keys.add(k);
            }
            catch (Throwable t2)
            {
                LOG.warn("rollback after applyAndPersist failure also failed", t2);
            }
        }
    }

}
