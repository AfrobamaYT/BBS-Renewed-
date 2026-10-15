package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class VanillaParticleFormRenderer extends FormRenderer<VanillaParticleForm> implements ITickable
{
    public static final Link PARTICLE_PREVIEW = new Link("minecraft", "textures/particle/flame.png");

    private Vector3d pos = new Vector3d();
    private Vector3f vel = new Vector3f();
    private Matrix3f rot = new Matrix3f();
    private int tick;

    public VanillaParticleFormRenderer(VanillaParticleForm form)
    {
        super(form);
    }

    @Override
    protected boolean flacheVorschau()
    {
        return true;
    }

    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(PARTICLE_PREVIEW);

        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;

        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);

        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Matrix4f matrix = new Matrix4f(InverseView.get());

        /* Since 1.21.1 the world render keeps the camera view in RenderSystem's global
         * model-view and gives the stack an identity base, so stack.last() no longer
         * carries the view rotation that InverseView is meant to cancel. Fold the global
         * model-view back in (identity, hence a no-op, in the form editor where the camera
         * lives in the stack) so the emitter's world position and direction come out right. */
        matrix.mul(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()));
        matrix.mul(context.stack.last().pose());

        Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));

        translation.add(camera.position().x, camera.position().y, camera.position().z);
        context.stack.pushPose();
        context.stack.setIdentity();
        context.stack.mulPose(new Matrix4f(InverseView.get()).invert());

        this.pos.set(translation);
        this.vel.set(0F, 0F, 1F);
        this.rot.set(matrix).transform(this.vel);

        context.stack.popPose();
    }

    @Override
    public void tick(IEntity entity)
    {
        Level world = entity.getWorld();
        boolean paused = this.form.paused.get();
        Vector3f temp3f = new Vector3f();

        if (world != null && !paused)
        {
            float velocity = this.form.velocity.get();
            int count = this.form.count.get();
            int frequency = this.form.frequency.get();

            if (this.tick <= 0)
            {
                Matrix3f m = Matrices.TEMP_3F;
                Vector3f v = Vectors.TEMP_3F;
                ParticleSettings settings = this.form.settings.get();
                ParticleOptions effect = createEffect(settings, world);

                for (int i = 0; i < count; i++)
                {
                    float velocityX = this.vel.x * velocity;
                    float velocityY = this.vel.y * velocity;
                    float velocityZ = this.vel.z * velocity;
                    float sh = MathUtils.toRad(this.form.scatteringYaw.get()) * (float) (Math.random() - 0.5D);
                    float sv = MathUtils.toRad(this.form.scatteringPitch.get()) * (float) (Math.random() - 0.5D);

                    m.identity()
                        .rotateY(sh)
                        .rotateX(sv)
                        .transform(v.set(velocityX, velocityY, velocityZ));

                    temp3f.set(
                        (Math.random() * 2F - 1F) * this.form.offsetX.get(),
                        (Math.random() * 2F - 1F) * this.form.offsetY.get(),
                        (Math.random() * 2F - 1F) * this.form.offsetZ.get()
                    );

                    if (this.form.local.get())
                    {
                        this.rot.transform(temp3f);
                    }

                    double x = this.pos.x + temp3f.x;
                    double y = this.pos.y + temp3f.y;
                    double z = this.pos.z + temp3f.z;

                    /* ⚠️ 26.2: addParticle hat einen ZWEITEN Schalter bekommen. Der erste ist
                     * unveraendert "force" (auch ausserhalb der Sichtweite erzeugen), der neue
                     * zweite ist "decreased" -- ob der Partikel unter niedriger
                     * Partikel-Einstellung ausgeduennt werden darf. false ist die alte
                     * Bedeutung: Vanillas 7-Argument-Ueberladung setzt ihn ebenfalls auf false
                     * (im Bytecode nachgelesen). Wer die Reihenfolge nicht mitzieht, schiebt
                     * die x-Koordinate in einen boolean. */
                    world.addParticle(effect, true, false, x, y, z, v.x, v.y, v.z);
                }

                this.tick = frequency;
            }

            this.tick -= 1;
        }
    }

    /**
     * Builds the particle effect from the form's settings. For the two common parametric
     * particles the arguments string is just a bare id for convenience — {@code dirt} for
     * {@code minecraft:block}, {@code apple} for {@code minecraft:item} — and the rest of the
     * structure is filled in here. {@code minecraft:entity_effect} takes the same shortcut with
     * a bare {@code "r g b [a]"} colour. Otherwise (or when a {@code {...}} compound is given)
     * it is parsed exactly like the vanilla {@code /particle} command, so every particle type
     * still works. Falls back to flame when nothing parses.
     */
    private static ParticleOptions createEffect(ParticleSettings settings, Level world)
    {
        String args = settings.arguments == null ? "" : settings.arguments.trim();

        try
        {
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(settings.particle);
            boolean bareId = !args.isEmpty() && args.charAt(0) != '{';

            if (bareId && type == ParticleTypes.BLOCK)
            {
                return new BlockParticleOption(ParticleTypes.BLOCK, BuiltInRegistries.BLOCK.getValue(Identifier.parse(args)).defaultBlockState());
            }

            if (bareId && type == ParticleTypes.ITEM)
            {
                /* 26.2: ItemParticleOption nimmt den Item, nicht mehr einen ItemStack. */
                //? if >=26.1 {
                /*return new ItemParticleOption(ParticleTypes.ITEM, BuiltInRegistries.ITEM.getValue(Identifier.parse(args)));
                *///?} else {
                return new ItemParticleOption(ParticleTypes.ITEM, new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(args))));
                //?}
            }

            if (bareId && type == ParticleTypes.ENTITY_EFFECT)
            {
                ParticleOptions tinted = parseEntityEffectColor(args);

                if (tinted != null)
                {
                    return tinted;
                }
            }

            StringReader reader = new StringReader(settings.particle.toString() + args);

            return ParticleArgument.readParticle(reader, world.registryAccess());
        }
        catch (Exception e)
        {
            return ParticleTypes.FLAME;
        }
    }

    /**
     * Parses the colour that the form editor's colour presets write:
     * {@code "r g b"} or {@code "r g b a"}, each 0..1, decimal point (Locale.ROOT — the editor
     * formats with the same locale, a comma would land here as a parse failure).
     *
     * <p>Vanilla's own spelling for this particle is the packed {@code {color:<argb>}} compound;
     * it starts with '{' and therefore never reaches this method, so both forms keep working.</p>
     *
     * @return null when the arguments are not a float triple/quad, so the caller falls through
     *         to the vanilla parser instead of swallowing a valid but unusual argument string.
     */
    private static ParticleOptions parseEntityEffectColor(String args)
    {
        String[] split = args.split("\\s+");

        if (split.length < 3 || split.length > 4)
        {
            return null;
        }

        try
        {
            float r = Float.parseFloat(split[0]);
            float g = Float.parseFloat(split[1]);
            float b = Float.parseFloat(split[2]);
            float a = split.length == 4 ? Float.parseFloat(split[3]) : 1F;

            /* 26.2: FastColor.ARGB32 heisst jetzt ARGB -- gleiche Methoden, gleiche
             * Argumentfolge (Alpha zuerst), nur ohne die innere Klasse. */
            return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, ARGB.colorFromFloat(a, r, g, b));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}