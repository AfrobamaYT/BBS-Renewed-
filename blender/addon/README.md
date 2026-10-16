# blender_bbs_bridge — versionierte Quelle

**Hier liegt die Wahrheit; `~/.config/blender/5.2/scripts/addons/blender_bbs_bridge/`
ist nur die installierte Kopie.** Aenderungen zuerst hier testen und committen,
dann die installierte Kopie mit Sicherung aus diesem Verzeichnis aktualisieren und
das Addon in Blender neu laden. Nie die alte installierte Kopie zurueck in den
Worktree spiegeln: Am 21.09.2026 war dort noch Version 0.16, waehrend hier 0.34
lag; ein Frischstart-Test lud dadurch falschen Code und verschluckte sogar den
fehlgeschlagenen Actor-Import. `tools/pruefe-blender-bridge.py` prueft seitdem
den tatsaechlich geladenen Quellpfad.

Warum versioniert (2026-08-16, Brücken-Durchtest): das Addon lebte NUR in `~/.config` —
beim Linux-Umzug ging `light_export.py` verloren (Import brach), `state.BBS_ASSETS_DIR`
zeigte noch auf den Windows-Pfad, und der Film-Kamera-Pitch-Bug (+90°-const fehlte) war
nirgends nachvollziehbar. Unversionierter Code ist stiller Verlust.

Weg-A-Gegenstück auf der Java-Seite: `com.blenderbridge.render.RenderComposite` (sendet
RENDER), Addon-Consumer ist `render_bridge.py` (Antwort RENDER_RESULT).

Modelle dürfen neben der schnellen BOBJ-Vorschau in `config.json` eine
`blender_source`-Collection aus einer lokalen `.blend` deklarieren. `native_model.py`
appendet diese Quelle für den Hybrid-Render vollständig und erhält dadurch Originalmesh,
Material-Nodes, Treiber, Korrektur-Shape-Keys und Armature-Modifier. Eine deklarierte,
aber kaputte Quelle bricht laut ab; sie fällt nicht still auf die BOBJ-Näherung zurück.
