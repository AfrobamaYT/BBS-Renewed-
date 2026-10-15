package mchorse.bbs_mod.ui.forms;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.BBSSettings;

import java.util.function.Consumer;

public class UIFormPalette extends UIElement implements IUIFormList
{
    public UIFormList list;
    public UIFormEditor editor;

    public Consumer<Form> callback;

    private UIFormCategory lastSelected;
    private boolean background = true;
    private boolean cantExit;
    private boolean immersive;
    private boolean canModify;

    public static UIFormPalette open(UIElement parent, boolean editing, Form form, Consumer<Form> callback)
    {
        return open(parent, editing, form, false, callback);
    }

    public static UIFormPalette open(UIElement parent, boolean editing, Form form, boolean ignore, Consumer<Form> callback)
    {
        UIContext context = parent.getContext();

        if (!ignore)
        {
            if (!parent.getRoot().getChildren(UIFormPalette.class).isEmpty() || context == null)
            {
                return null;
            }
        }

        context.unfocus();

        UIFormPalette palette = new UIFormPalette(callback);

        palette.resetFlex().full(parent);
        palette.resize();

        parent.add(palette);

        palette.setSelected(form);
        palette.edit(editing);

        return palette;
    }

    public UIFormPalette(Consumer<Form> callback)
    {
        this.callback = callback;

        this.list = new UIFormList(this);
        this.list.full(this);

        this.editor = new UIFormEditor(this);
        this.editor.full(this);
        this.editor.setVisible(false);

        this.add(this.list, this.editor);

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).markContainer();

        this.keys().register(Keys.FORMS_EDIT, () ->
        {
            if (!this.editor.isEditing())
            {
                this.toggleEditor();
            }
        });
    }

    public void noBackground()
    {
        this.background = false;
    }

    public void cantExit()
    {
        this.cantExit = true;

        this.list.close.removeFromParent();
        this.eventPropagataion(EventPropagation.PASS);
    }

    public void canModify()
    {
        this.canModify = true;
    }

    public boolean isImmersive()
    {
        return this.immersive;
    }

    public void immersive()
    {
        this.immersive = true;
    }

    public UIFormPalette updatable()
    {
        this.editor.renderer.updatable();

        return this;
    }

    public void edit(boolean editing)
    {
        if (editing != this.editor.isEditing())
        {
            this.toggleEditor();
        }
    }

    @Override
    public void exit()
    {
        if (!this.editor.isEditing())
        {
            if (!this.cantExit)
            {
                this.removeFromParent();
            }
        }
        else
        {
            this.toggleEditor();
        }
    }

    @Override
    public void toggleEditor()
    {
        this.events.emit(new UIToggleEditorEvent(this, !this.editor.isEditing()));

        if (!this.editor.isEditing())
        {
            Form form = this.list.getSelected();

            if (this.editor.edit(form))
            {
                this.lastSelected = this.list.getSelectedCategory();
            }
        }
        else
        {
            Form form = this.editor.finish();

            if (this.canModify && this.lastSelected.category.canModify(form))
            {
                int index = this.lastSelected.category.getForms().indexOf(this.lastSelected.selected);

                if (index >= 0)
                {
                    this.lastSelected.category.replaceForm(index, form);
                }
            }

            this.list.setSelected(form);
            this.accept(form);

            this.lastSelected = null;
        }

        this.list.setVisible(!this.editor.isEditing());
        this.editor.setVisible(this.editor.isEditing());
    }

    @Override
    public void accept(Form form)
    {
        if (this.callback != null)
        {
            this.callback.accept(form);
        }
    }

    public void setSelected(Form form)
    {
        this.list.setSelected(form);
        this.list.scrollToSelected();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(InputConstants.KEY_ESCAPE))
        {
            boolean wasEditing = this.editor.isEditing();

            this.exit();

            if (!this.cantExit)
            {
                return true;
            }

            if (wasEditing)
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        /* ⛔ WAS UNTER UNS LIEGT, MUSS VOR UNS GEZEICHNET WERDEN (2026-08-05, Bugliste #17d).
         *
         * Die Palette deckt den ganzen Schirm ab, das Filmpanel darunter zeichnet aber weiter
         * -- und seine Formvorschauen reihen sich in die Miniaturschlange ein, die erst am
         * ENDE des Durchlaufs abgegeben wird. Ergebnis: die Replay-Miniaturen des VERDECKTEN
         * Panels landen als Letztes im Bild und liegen damit OBEN AUF der Palette.
         *
         * Gemessen: im offenen Form-Editor standen drei winzige Modelle am linken Buehnenrand,
         * bei exakt denselben GUI-Koordinaten (~162, 170-230) wie die Replay-Miniaturen des
         * Filmpanels darunter. Der Nutzer meldete es als "es werden immernoch Miniaturen im
         * Edit-Form gerendert, die hier nichts zu suchen haben".
         *
         * In unserer 1.21.1 gab es das nicht, weil dort SOFORT gezeichnet wurde: was vor der
         * Palette dran war, wurde von ihr ueberdeckt. Diese Zeile stellt genau diese
         * Reihenfolge wieder her -- alles Wartende geht raus, BEVOR die Palette zeichnet.
         *
         * Es ist bewusst hier und nicht in Miniaturschlange: nur ein Element, das andere
         * VERDECKT, weiss, dass es eine Grenze in der Zeichenreihenfolge ist. */
        /* ⚠️ GEGENGEPRUEFT, DASS ES DIE PALETTE NICHT IHRE EIGENEN MINIATUREN KOSTET: A/B im
         * selben Build ueber einen Laufzeitschalter, Palettenliste beide Male 5730 bunte
         * Proben. (Die fehlenden Modell-Miniaturen IN der Liste sind ein eigener, aelterer
         * Befund -- sie fehlen mit und ohne diese Zeile.) */
        mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.abgeben(
            context, context.menu.width, context.menu.height);

        if (this.background)
        {
            if (!this.immersive || this.list.isVisible())
            {
                this.area.render(context.batcher, BBSSettings.baseSurface());
            }
        }

        /* Panels are the deep surface, so inputs take the raised (lighter) surface to stand out -
         * same as the film editor. Sections flip this back to false for their own inner inputs. */
        boolean lightInputs = BBSSettings.lightInputs;

        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = lightInputs;
        }
    }
}
