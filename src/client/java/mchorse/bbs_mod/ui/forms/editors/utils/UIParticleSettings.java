package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.particles.ParticleType;

public class UIParticleSettings extends UIElement
{
    public UIButton particle;
    public UITextbox arguments;

    private ParticleSettings settings;

    /** Fired after the settings were edited here, so dependent UI (colour presets) can follow. */
    private Consumer<ParticleSettings> changeCallback;

    public UIParticleSettings()
    {
        this.particle = new UIButton(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EDITOR_PICK, (b) ->
        {
            UIListOverlayPanel overlayPanel = new UIListOverlayPanel(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EDITOR_TITLE, (l) -> this.setParticle(Identifier.parse(l)));
            List<String> strings = new ArrayList<>();

            for (ResourceKey<ParticleType<?>> key : BuiltInRegistries.PARTICLE_TYPE.registryKeySet())
            {
                strings.add(key.identifier().toString());
            }

            overlayPanel.addValues(strings);
            overlayPanel.list.list.sort();
            overlayPanel.setValue(this.settings.particle.toString());

            UIOverlay.addOverlay(this.getContext(), overlayPanel);
        });

        this.arguments = new UITextbox(1000, this::setArguments);

        this.column().vertical().stretch();
        this.add(this.particle, this.arguments);
    }

    public UIParticleSettings callback(Consumer<ParticleSettings> callback)
    {
        this.changeCallback = callback;

        return this;
    }

    public void setSettings(ParticleSettings settings)
    {
        this.settings = settings;

        this.arguments.setText(settings.arguments);
    }

    public ParticleSettings getSettings()
    {
        return this.settings;
    }

    /**
     * Writes the arguments from the outside (colour presets). Goes through
     * {@link UITextbox#setText(String)}, which does NOT re-fire the textbox callback — that is
     * what keeps preset -> textbox -> preset from looping.
     */
    public void setArgumentsText(String args)
    {
        this.settings.arguments = args;
        this.arguments.setText(args);
    }

    protected void setParticle(Identifier id)
    {
        this.settings.particle = id;

        this.notifyChange();
    }

    protected void setArguments(String args)
    {
        this.settings.arguments = args;

        this.notifyChange();
    }

    private void notifyChange()
    {
        if (this.changeCallback != null)
        {
            this.changeCallback.accept(this.settings);
        }
    }
}