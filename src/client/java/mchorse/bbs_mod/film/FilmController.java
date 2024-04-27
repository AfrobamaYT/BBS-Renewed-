package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.properties.AnchorProperty;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.math.Interpolations;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Refactor with UIFilmController
 */
public class FilmController
{
    public Film film;

    private List<IEntity> entities = new ArrayList<>();

    private int tick;
    private int duration;

    public static Matrix4f getEntityMatrix(List<IEntity> entities, WorldRenderContext context, AnchorProperty.Anchor selector, Matrix4f defaultMatrix)
    {
        int entityIndex = selector.actor;

        if (CollectionUtils.inRange(entities, entityIndex))
        {
            IEntity entity = entities.get(entityIndex);
            Matrix4f basic = new Matrix4f(getMatrixForRenderWithRotation(entity, context.camera(), context.tickDelta()));

            Map<String, Matrix4f> map = new HashMap<>();
            MatrixStack stack = new MatrixStack();

            Form form = entity.getForm();

            if (form != null)
            {
                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, map, "", context.tickDelta());

                Matrix4f matrix = map.get(selector.attachment);

                if (matrix != null)
                {
                    basic.mul(matrix);
                }
            }

            return basic;
        }

        return defaultMatrix;
    }

    public static Matrix4f getMatrixForRenderWithRotation(IEntity entity, net.minecraft.client.render.Camera camera, float tickDelta)
    {
        double x = Interpolations.lerp(entity.getPrevX(), entity.getX(), tickDelta) - camera.getPos().x;
        double y = Interpolations.lerp(entity.getPrevY(), entity.getY(), tickDelta) - camera.getPos().y;
        double z = Interpolations.lerp(entity.getPrevZ(), entity.getZ(), tickDelta) - camera.getPos().z;

        Matrix4f matrix = new Matrix4f();

        float bodyYaw = Interpolations.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        matrix.translate((float) x, (float) y, (float) z);
        matrix.rotateY(MathUtils.toRad(-bodyYaw));

        return matrix;
    }

    public FilmController(Film film)
    {
        this.film = film;
        this.duration = film.camera.calculateDuration();

        for (Replay replay : film.replays.getList())
        {
            World world = MinecraftClient.getInstance().world;
            IEntity entity = new StubEntity(world);

            entity.setForm(FormUtils.copy(replay.form.get()));
            replay.applyFrame(0, entity);
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());

            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevPitch(entity.getPitch());
            entity.setPrevBodyYaw(entity.getBodyYaw());

            this.entities.add(entity);
        }
    }

    public boolean update()
    {
        this.tick += 1;

        for (int i = 0; i < this.entities.size(); i++)
        {
            IEntity entity = this.entities.get(i);

            entity.update();

            if (entity.getForm() != null)
            {
                entity.getForm().update(entity);
            }

            List<Replay> replays = film.replays.getList();

            if (CollectionUtils.inRange(replays, i))
            {
                Replay replay = replays.get(i);
                int ticks = this.tick;

                replay.applyFrame(ticks, entity, null);
                replay.applyProperties(ticks, entity.getForm(), true);
            }
        }

        return this.tick >= this.duration;
    }

    public void render(WorldRenderContext context)
    {
        for (IEntity entity : this.entities)
        {
            this.renderEntity(context, entity, null);
        }
    }

    private void renderEntity(WorldRenderContext context, IEntity entity, StencilMap map)
    {
        Form form = entity.getForm();

        if (form != null)
        {
            RenderSystem.enableDepthTest();

            MatrixStack stack = context.matrixStack();
            Vector3d position = Vectors.TEMP_3D.set(entity.getPrevX(), entity.getPrevY(), entity.getPrevZ())
                .lerp(new Vector3d(entity.getX(), entity.getY(), entity.getZ()), context.tickDelta());
            int light = WorldRenderer.getLightmapCoordinates(entity.getWorld(), new BlockPos((int) position.x, (int) (position.y + 0.5D), (int) position.z));

            FormRenderingContext formContext = FormRenderingContext
                .set(entity, stack, light, context.tickDelta())
                .camera(context.camera())
                .stencilMap(map);

            AnchorProperty.Anchor value = form.anchor.get();
            AnchorProperty.Anchor last = form.anchor.getLast();

            if (value != null && last != null)
            {
                Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, context.camera(), context.tickDelta());
                Matrix4f matrix = getEntityMatrix(this.entities, context, value, defaultMatrix);
                Matrix4f lastMatrix = getEntityMatrix(this.entities, context, last, defaultMatrix);

                if (matrix != null && lastMatrix != null && matrix != lastMatrix)
                {
                    float factor = form.anchor.getTweenFactorInterpolated(context.tickDelta());

                    stack.push();
                    MatrixStackUtils.multiply(stack, Matrices.lerp(lastMatrix, matrix, factor));
                    FormUtilsClient.render(form, formContext);
                    stack.pop();

                    RenderSystem.disableDepthTest();

                    return;
                }
            }

            stack.push();
            MatrixStackUtils.multiply(stack, getMatrixForRenderWithRotation(entity, context.camera(), context.tickDelta()));
            FormUtilsClient.render(form, formContext);
            stack.pop();

            stack.push();

            if (map == null)
            {
                Camera camera = context.camera();
                double x = Interpolations.lerp(entity.getPrevX(), entity.getX(), context.tickDelta());
                double y = Interpolations.lerp(entity.getPrevY(), entity.getY(), context.tickDelta());
                double z = Interpolations.lerp(entity.getPrevZ(), entity.getZ(), context.tickDelta());

                stack.translate(x - camera.getPos().x, y - camera.getPos().y, z - camera.getPos().z);
                ModelBlockEntityRenderer.renderShadow(context.consumers(), stack, context.tickDelta(), x, y, z, 0F, 0F, 0F);
            }

            stack.pop();

            RenderSystem.disableDepthTest();
        }
    }
}