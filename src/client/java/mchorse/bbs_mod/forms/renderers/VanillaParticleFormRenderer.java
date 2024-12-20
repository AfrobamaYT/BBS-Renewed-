package mchorse.bbs_mod.forms.renderers;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class VanillaParticleFormRenderer extends FormRenderer<VanillaParticleForm> implements ITickable
{
    public static final Link ANCHOR_PREVIEW = Link.assets("textures/anchor.png");

    private Vector3f vec = new Vector3f();
    private Vector3d pos = new Vector3d();
    private Vector3f vel = new Vector3f();
    private int tick;

    public VanillaParticleFormRenderer(VanillaParticleForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(ANCHOR_PREVIEW);

        int w = texture.width;
        int h = texture.height;
        int x = (x1 + x2) / 2;
        int y = (y1 + y2) / 2;

        context.batcher.fullTexturedBox(texture, x - w / 2, y - h / 2, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Matrix4f matrix = new Matrix4f(RenderSystem.getInverseViewRotationMatrix());

        matrix.mul(context.stack.peek().getPositionMatrix());

        Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));

        translation.add(camera.getPos().x, camera.getPos().y, camera.getPos().z);
        context.stack.push();
        context.stack.loadIdentity();
        context.stack.multiplyPositionMatrix(new Matrix4f(RenderSystem.getInverseViewRotationMatrix()).invert());

        this.pos.set(translation);
        this.vel.set(0F, 0F, 1F);
        Matrices.TEMP_3F.set(matrix).transform(this.vel);

        context.stack.pop();
    }

    @Override
    public void tick(IEntity entity)
    {
        World world = entity.getWorld();
        boolean paused = this.form.paused.get(0F);

        if (world != null && !paused)
        {
            float velocity = this.form.velocity.get(0F);
            int count = this.form.count.get(0F);
            int frequency = this.form.frequency.get(0F);

            if (this.tick <= 0)
            {
                Matrix3f m = Matrices.TEMP_3F;
                Vector3f v = Vectors.TEMP_3F;
                ParticleSettings settings = this.form.settings.get(0F);
                ParticleType type = Registries.PARTICLE_TYPE.get(settings.particle);
                ParticleEffect effect = ParticleTypes.FLAME;

                try
                {
                    if (type != null)
                    {
                        effect = type.getParametersFactory().read(type, new StringReader(" " + settings.arguments));
                    }
                }
                catch (Exception e)
                {}

                for (int i = 0; i < count; i++)
                {
                    float velocityX = this.vel.x * velocity;
                    float velocityY = this.vel.y * velocity;
                    float velocityZ = this.vel.z * velocity;
                    float sh = MathUtils.toRad(this.form.scatteringYaw.get(0F)) * (float) (Math.random() - 0.5D);
                    float sv = MathUtils.toRad(this.form.scatteringPitch.get(0F)) * (float) (Math.random() - 0.5D);

                    m.identity()
                        .rotateY(sh)
                        .rotateX(sv)
                        .transform(v.set(velocityX, velocityY, velocityZ));

                    double x = this.pos.x + ((Math.random() * 2F - 1F) * this.form.offsetX.get(0F));
                    double y = this.pos.y + ((Math.random() * 2F - 1F) * this.form.offsetY.get(0F));
                    double z = this.pos.z + ((Math.random() * 2F - 1F) * this.form.offsetZ.get(0F));

                    world.addParticle(effect, true, x, y, z, v.x, v.y, v.z);
                }

                this.tick = frequency;
            }

            this.tick -= 1;
        }
    }
}