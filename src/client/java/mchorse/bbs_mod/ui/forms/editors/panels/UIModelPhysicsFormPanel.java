package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UIModelPhysicsFormPanel extends UIFormPanel<ModelForm>
{
    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final int DEFAULT_ITERATIONS = 4;

    public UIList<String> chains;

    public UIButton add;
    public UIButton remove;
    public UIButton attach;
    public UIButton root;
    public UIButton end;
    public UITrackpad gravity;
    public UITrackpad damping;
    public UITrackpad iterations;
    public UIButton apply;

    private List<String> availableBones = Collections.emptyList();
    private List<ChainData> data = new ArrayList<>();
    private int selected = -1;
    private ModelInstance modelInstance;

    private static class ChainData
    {
        public String attach = "";
        public String root = "";
        public String end = "";
        public float gravity = DEFAULT_GRAVITY;
        public float damping = DEFAULT_DAMPING;
        public int iterations = DEFAULT_ITERATIONS;
    }

    public UIModelPhysicsFormPanel(UIForm editor)
    {
        super(editor);

        this.chains = new UIList<>((l) ->
        {
            this.selected = l.isEmpty() ? -1 : this.chains.getIndex();
            this.updateFields();
        })
        {
            @Override
            protected String elementToString(mchorse.bbs_mod.ui.framework.UIContext context, int i, String element)
            {
                return element;
            }
        };
        this.chains.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);

        this.add = new UIButton(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ADD, (b) ->
        {
            if (this.availableBones.isEmpty())
            {
                return;
            }

            ChainData chain = new ChainData();
            chain.root = this.availableBones.get(0);
            chain.end = chain.root;
            chain.attach = "";

            this.data.add(chain);
            this.reloadChainList();
            this.selectIndex(this.data.size() - 1);
        });

        this.remove = new UIButton(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_REMOVE, (b) ->
        {
            if (this.selected < 0 || this.selected >= this.data.size())
            {
                return;
            }

            this.data.remove(this.selected);
            this.reloadChainList();

            if (this.data.isEmpty())
            {
                this.selectIndex(-1);
            }
            else
            {
                this.selectIndex(Math.min(this.selected, this.data.size() - 1));
            }
        });

        this.attach = new UIButton(IKey.EMPTY, (b) ->
        {
            ChainData chain = this.getSelectedChain();

            if (chain == null)
            {
                return;
            }

            this.openBoneMenu(chain.attach, true, (bone) ->
            {
                chain.attach = bone;
                this.updateFields();
                this.reloadChainList();
            });
        });

        this.root = new UIButton(IKey.EMPTY, (b) ->
        {
            ChainData chain = this.getSelectedChain();

            if (chain == null)
            {
                return;
            }

            this.openBoneMenu(chain.root, false, (bone) ->
            {
                chain.root = bone;
                this.updateFields();
                this.reloadChainList();
            });
        });

        this.end = new UIButton(IKey.EMPTY, (b) ->
        {
            ChainData chain = this.getSelectedChain();

            if (chain == null)
            {
                return;
            }

            this.openBoneMenu(chain.end, false, (bone) ->
            {
                chain.end = bone;
                this.updateFields();
                this.reloadChainList();
            });
        });

        this.gravity = new UITrackpad((v) ->
        {
            ChainData chain = this.getSelectedChain();

            if (chain != null)
            {
                chain.gravity = v.floatValue();
            }
        });
        this.gravity.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.01D).limit(0D, 10D);
        this.gravity.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY);

        this.damping = new UITrackpad((v) ->
        {
            ChainData chain = this.getSelectedChain();

            if (chain != null)
            {
                chain.damping = v.floatValue();
            }
        });
        this.damping.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
        this.damping.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING);

        this.iterations = new UITrackpad((v) ->
        {
            ChainData chain = this.getSelectedChain();

            if (chain != null)
            {
                chain.iterations = v.intValue();
            }
        });
        this.iterations.onlyNumbers().integer().values(1D).increment(1D).limit(1D, 20D, true);
        this.iterations.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS);

        this.apply = new UIButton(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_APPLY, (b) -> this.save());

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CHAINS),
            this.chains,
            UI.row(this.add, this.remove),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SETTINGS).background().marginTop(UIConstants.SECTION_GAP),
            this.attach,
            this.root,
            this.end,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY),
            this.gravity,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING),
            this.damping,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS),
            this.iterations,
            this.apply.marginTop(UIConstants.SECTION_GAP)
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);
        this.modelInstance = model;

        if (model == null || model.model == null)
        {
            this.availableBones = Collections.emptyList();
            this.data.clear();
            this.reloadChainList();
            this.selectIndex(-1);
            this.setElementsEnabled(false);
        }
        else
        {
            List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
            bones.removeIf(model.disabledBones::contains);
            this.availableBones = bones;

            this.setElementsEnabled(true);
            this.load();
            this.reloadChainList();

            if (!this.data.isEmpty())
            {
                this.selectIndex(0);
            }
            else
            {
                this.selectIndex(-1);
            }
        }

        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.chains.setEnabled(enabled);
        this.add.setEnabled(enabled);
        this.remove.setEnabled(enabled);
        this.attach.setEnabled(enabled);
        this.root.setEnabled(enabled);
        this.end.setEnabled(enabled);
        this.gravity.setEnabled(enabled);
        this.damping.setEnabled(enabled);
        this.iterations.setEnabled(enabled);
        this.apply.setEnabled(enabled);
    }

    private ChainData getSelectedChain()
    {
        return this.selected >= 0 && this.selected < this.data.size() ? this.data.get(this.selected) : null;
    }

    private void selectIndex(int index)
    {
        this.selected = index;

        if (index < 0 || index >= this.chains.getList().size())
        {
            this.chains.deselect();
        }
        else
        {
            this.chains.setIndex(index);
            this.chains.scroll.setScroll(index * this.chains.scroll.scrollItemSize);
        }

        this.updateFields();
    }

    private void reloadChainList()
    {
        int keep = this.selected;
        List<String> labels = new ArrayList<>(this.data.size());

        for (ChainData chain : this.data)
        {
            String attach = chain.attach == null || chain.attach.isEmpty() ? chain.root : chain.attach;
            labels.add(chain.root + " -> " + chain.end + " (" + attach + ")");
        }

        this.chains.setList(labels);

        if (keep >= 0 && keep < this.data.size())
        {
            this.selectIndex(keep);
        }
    }

    @Override
    public void pickBone(String bone)
    {
        if (bone == null || bone.isEmpty() || this.data.isEmpty())
        {
            return;
        }

        for (int i = 0; i < this.data.size(); i++)
        {
            ChainData chain = this.data.get(i);

            if (bone.equals(chain.root) || bone.equals(chain.end) || bone.equals(chain.attach))
            {
                this.selectIndex(i);
                return;
            }
        }
    }

    private void updateFields()
    {
        ChainData chain = this.getSelectedChain();

        boolean enabled = chain != null && this.chains.isEnabled();

        this.remove.setEnabled(enabled);
        this.attach.setEnabled(enabled);
        this.root.setEnabled(enabled);
        this.end.setEnabled(enabled);
        this.gravity.setEnabled(enabled);
        this.damping.setEnabled(enabled);
        this.iterations.setEnabled(enabled);
        this.apply.setEnabled(this.chains.isEnabled());

        if (chain == null)
        {
            this.attach.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ATTACH.format("-");
            this.root.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ROOT.format("-");
            this.end.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format("-");
            this.gravity.setValue(0);
            this.damping.setValue(0);
            this.iterations.setValue(0);
            return;
        }

        String attach = chain.attach == null || chain.attach.isEmpty() ? chain.root : chain.attach;

        this.attach.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ATTACH.format(attach);
        this.root.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ROOT.format(chain.root);
        this.end.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format(chain.end);
        this.gravity.setValue(chain.gravity);
        this.damping.setValue(chain.damping);
        this.iterations.setValue(chain.iterations);
    }

    private void openBoneMenu(String current, boolean allowEmpty, java.util.function.Consumer<String> callback)
    {
        if (this.availableBones.isEmpty())
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            if (allowEmpty)
            {
                boolean selected = current == null || current.isEmpty();
                menu.action(Icons.CHECKMARK, UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ATTACH_USE_ROOT, selected, () -> callback.accept(""));
            }

            for (String bone : this.availableBones)
            {
                boolean selected = bone.equals(current);
                menu.action(Icons.LIMB, IKey.constant(bone), selected, () -> callback.accept(bone));
            }
        });
    }

    private void load()
    {
        this.data.clear();
        String model = this.form.model.get();
        ModelPhysicsConfig config = model == null ? null : ModelPhysicsIO.read(model);

        if (config == null || config.chains() == null)
        {
            return;
        }

        for (ModelPhysicsConfig.Chain entry : config.chains())
        {
            if (entry == null || entry.root() == null || entry.end() == null)
            {
                continue;
            }

            ChainData chain = new ChainData();
            chain.attach = entry.attach();
            chain.root = entry.root();
            chain.end = entry.end();
            chain.gravity = entry.gravity();
            chain.damping = entry.damping();
            chain.iterations = entry.iterations();

            if (!chain.root.isEmpty() && !chain.end.isEmpty())
            {
                this.data.add(chain);
            }
        }
    }

    private void save()
    {
        String model = this.form.model.get();

        if (model == null || model.isEmpty())
        {
            this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVE_ERROR);
            return;
        }

        for (ChainData chain : this.data)
        {
            if (!this.isValidChain(model, chain))
            {
                this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_INVALID_CHAIN.format(chain.root, chain.end));
                return;
            }
        }

        List<ModelPhysicsConfig.Chain> chains = new ArrayList<>(this.data.size());

        for (ChainData chain : this.data)
        {
            chains.add(new ModelPhysicsConfig.Chain(
                chain.attach,
                chain.root,
                chain.end,
                chain.gravity,
                chain.damping,
                Math.max(1, chain.iterations)
            ));
        }

        if (ModelPhysicsIO.write(model, new ModelPhysicsConfig(chains)))
        {
            this.getContext().notifySuccess(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVED);
        }
        else
        {
            this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVE_ERROR);
        }
    }

    private boolean isValidChain(String modelId, ChainData chain)
    {
        if (this.modelInstance == null || this.modelInstance.model == null)
        {
            return true;
        }

        if (!(this.modelInstance.model instanceof Model model))
        {
            return true;
        }

        ModelGroup root = model.getGroup(chain.root);
        ModelGroup end = model.getGroup(chain.end);

        if (root == null || end == null)
        {
            return false;
        }

        ModelGroup group = end;

        while (group != null)
        {
            if (group == root)
            {
                return true;
            }

            group = group.parent;
        }

        return false;
    }

    
}
