package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import net.minecraft.client.Minecraft;

/**
 * Undo and redo for the model block panel: every edit of a block's properties is one entry holding
 * the block's data before and after; the updates of one gesture (dragging a number, the transform)
 * share an entry. Undoing restores the data, shows the block and saves it to the server. From BBS FS
 * cdfee525e (Wemppy4/bbs-fs, MIT).
 */
final class ModelBlockVerlauf
{
    final UndoManager<UIModelBlockPanel> undo = new UndoManager<>(100);

    /** Over the whole panel and added last, so it hears Ctrl+Z/R before the transform's R and the rest */
    final mchorse.bbs_mod.ui.framework.elements.UIElement tasten = new mchorse.bbs_mod.ui.framework.elements.UIElement();

    private final UIModelBlockPanel panel;
    private MapType transformVorher;

    ModelBlockVerlauf(UIModelBlockPanel panel)
    {
        this.panel = panel;

        this.tasten.keys().register(Keys.UNDO, () -> this.gehen(false)).active(() -> panel.getModelBlock() != null);
        this.tasten.keys().register(Keys.REDO, () -> this.gehen(true)).active(() -> panel.getModelBlock() != null);
    }

    /** One edit of the open block; edits with the same key in a row share an entry */
    void aendern(String key, Runnable aenderung)
    {
        ModelBlockEntity block = this.panel.getModelBlock();
        MapType vorher = (MapType) block.getProperties().toData().copy();

        aenderung.run();
        this.festhalten(block, vorher, key);
    }

    void festhalten(ModelBlockEntity block, MapType vorher, String key)
    {
        MapType nachher = block.getProperties().toData();

        if (vorher != null && !vorher.equals(nachher))
        {
            this.undo.pushUndo(new Eintrag(block, vorher, nachher, key));
        }
    }

    void transformAnfang()
    {
        this.transformVorher = (MapType) this.panel.getModelBlock().getProperties().toData().copy();
    }

    void transformEnde()
    {
        this.festhalten(this.panel.getModelBlock(), this.transformVorher, "transform");
    }

    private void gehen(boolean redo)
    {
        this.undo.markLastUndoNoMerging();

        if (redo ? this.undo.redo(this.panel) : this.undo.undo(this.panel))
        {
            this.undo.markLastUndoNoMerging();
        }
    }

    private static void wiederherstellen(UIModelBlockPanel panel, ModelBlockEntity block, MapType data)
    {
        Minecraft mc = Minecraft.getInstance();

        if (block.isRemoved() || block.getLevel() != mc.level)
        {
            return;
        }

        boolean global = block.getProperties().isGlobal();

        block.getProperties().fromData((MapType) data.copy());
        panel.fill(block, true);
        panel.save(block);

        /* "global" decides whether the block sits in the section mesh */
        if (global != block.getProperties().isGlobal() && mc.level != null)
        {
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
        }
    }

    private static final class Eintrag implements IUndo<UIModelBlockPanel>
    {
        private final ModelBlockEntity block;
        private final MapType vorher;
        private MapType nachher;
        private final String key;
        private boolean mergeable = true;

        private Eintrag(ModelBlockEntity block, MapType vorher, MapType nachher, String key)
        {
            this.block = block;
            this.vorher = (MapType) vorher.copy();
            this.nachher = (MapType) nachher.copy();
            this.key = key;
        }

        @Override
        public IUndo<UIModelBlockPanel> noMerging()
        {
            this.mergeable = false;

            return this;
        }

        @Override
        public boolean isMergeable(IUndo<UIModelBlockPanel> undo)
        {
            return this.mergeable && this.key != null && undo instanceof Eintrag other && this.block == other.block && this.key.equals(other.key);
        }

        @Override
        public void merge(IUndo<UIModelBlockPanel> undo)
        {
            this.nachher = ((Eintrag) undo).nachher;
        }

        @Override
        public void undo(UIModelBlockPanel panel)
        {
            wiederherstellen(panel, this.block, this.vorher);
        }

        @Override
        public void redo(UIModelBlockPanel panel)
        {
            wiederherstellen(panel, this.block, this.nachher);
        }
    }
}
