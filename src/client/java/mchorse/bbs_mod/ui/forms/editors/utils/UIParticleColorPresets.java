package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Colour presets for the vanilla particle form.
 *
 * <p>{@code minecraft:entity_effect} is the one vanilla particle whose colour is a free
 * parameter, and vanilla only spells it as a packed ARGB integer ({@code {color:-65536}}) —
 * unusable by hand. This element offers the status effect colours as presets instead and
 * writes them into the particle's arguments as {@code "r g b a"} floats, which
 * {@code VanillaParticleFormRenderer} understands.</p>
 *
 * <p>Invariant: {@link #ARGUMENT_FORMAT} is shared with the renderer. Changing it silently
 * breaks every already saved particle form, so change both sides or neither.</p>
 */
public class UIParticleColorPresets extends UIElement
{
    /** Locale.ROOT — a comma decimal separator would not survive the renderer's parse. */
    public static final String ARGUMENT_FORMAT = "%.2f %.2f %.2f %.1f";

    private static final IKey EFFECT = L10n.lang("bbs.ui.forms.editors.vanilla_particle.effect");
    private static final IKey PICK_EFFECT = L10n.lang("bbs.ui.forms.editors.vanilla_particle.pick_effect");
    private static final IKey EFFECT_TITLE = L10n.lang("bbs.ui.forms.editors.vanilla_particle.effect_title");

    public final UIButton pick;

    private final Consumer<String> callback;

    /**
     * Whether the given particle takes a colour at all.
     *
     * <p>Compares the resolved type rather than matching the id's path: in 1.21.1
     * {@code minecraft:effect} and {@code minecraft:instant_effect} are plain
     * {@link net.minecraft.core.particles.SimpleParticleType}s that ignore any colour, so a
     * {@code path.contains("effect")} test would offer presets that do nothing.</p>
     */
    public static boolean supportsColor(ResourceLocation particle)
    {
        return particle != null && BuiltInRegistries.PARTICLE_TYPE.get(particle) == ParticleTypes.ENTITY_EFFECT;
    }

    /** The status effect's own colour, in the argument form the renderer parses. */
    public static String toArguments(MobEffect effect)
    {
        int color = effect.getColor();

        return String.format(Locale.ROOT, ARGUMENT_FORMAT,
            ((color >> 16) & 0xFF) / 255F,
            ((color >> 8) & 0xFF) / 255F,
            (color & 0xFF) / 255F,
            1F
        );
    }

    public UIParticleColorPresets(Consumer<String> callback)
    {
        this.callback = callback;

        this.pick = new UIButton(PICK_EFFECT, (b) -> this.openPicker());

        this.column().vertical().stretch();
        this.add(UI.label(EFFECT), this.pick);
    }

    /**
     * Re-reads the settings: shows or hides the whole block and names the button after the
     * preset that matches the current arguments (if any). Must be called after every change
     * to the particle id or the arguments, otherwise the button keeps a stale name.
     */
    public void update(ParticleSettings settings)
    {
        boolean supported = settings != null && supportsColor(settings.particle);

        this.setVisible(supported);

        if (supported)
        {
            ResourceLocation preset = this.findPreset(settings.arguments);

            this.pick.label = preset == null ? PICK_EFFECT : IKey.raw(preset.toString());
        }
    }

    /**
     * Reverse lookup: which preset produced these arguments. Compares the formatted strings
     * rather than the numbers, so a hand-typed colour that no preset produces stays unnamed
     * instead of being labelled with a near miss.
     */
    private ResourceLocation findPreset(String arguments)
    {
        String args = arguments == null ? "" : arguments.trim();

        if (args.isEmpty())
        {
            return null;
        }

        for (ResourceKey<MobEffect> key : BuiltInRegistries.MOB_EFFECT.registryKeySet())
        {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(key);

            if (effect != null && args.equals(toArguments(effect)))
            {
                return key.location();
            }
        }

        return null;
    }

    private void openPicker()
    {
        UIListOverlayPanel panel = new UIListOverlayPanel(EFFECT_TITLE, (l) -> this.setEffect(new ResourceLocation(l)));
        List<String> ids = new ArrayList<>();

        for (ResourceKey<MobEffect> key : BuiltInRegistries.MOB_EFFECT.registryKeySet())
        {
            ids.add(key.location().toString());
        }

        panel.addValues(ids);
        panel.list.list.sort();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void setEffect(ResourceLocation id)
    {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);

        if (effect == null)
        {
            return;
        }

        this.pick.label = IKey.raw(id.toString());
        this.callback.accept(toArguments(effect));
    }
}
