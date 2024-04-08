package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import net.minecraft.block.BlockState;

public class UIBlockFormPanel extends UIFormPanel<BlockForm>
{
    public UIBlockStateEditor editor;

    public UIBlockFormPanel(UIForm editor)
    {
        super(editor);

        this.editor = new UIBlockStateEditor((blockState) ->
        {
            this.form.blockState.set(blockState);
        });

        this.options.add(this.editor);
    }

    @Override
    public void startEdit(BlockForm form)
    {
        super.startEdit(form);

        BlockState blockState = this.form.blockState.get();

        this.editor.setBlockState(blockState);
        this.editor.updateBlockList();
    }
}