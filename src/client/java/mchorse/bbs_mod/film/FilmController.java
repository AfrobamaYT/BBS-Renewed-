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
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilmController
{
    public Film film;

    protected List<IEntity> entities = new ArrayList<>();

    public int exception = -1;
    public int tick;
    public int duration;

    /* Rendering helpers */

    public static void renderEntity(List<IEntity> entities, WorldRenderContext context, IEntity entity, StencilMap map)
    {
        renderEntity(entities, entity, context.camera(), context.matrixStack(), context.consumers(), map, context.tickDelta());
    }

    public static void renderEntity(List<IEntity> entities, IEntity entity, Camera camera, MatrixStack stack, VertexConsumerProvider consumers, StencilMap map, float transition)
    {
        Form form = entity.getForm();

        if (form == null)
        {
            return;
        }

        Vector3d position = Vectors.TEMP_3D.set(entity.getPrevX(), entity.getPrevY(), entity.getPrevZ())
            .lerp(new Vector3d(entity.getX(), entity.getY(), entity.getZ()), transition);

        AnchorProperty.Anchor value = form.anchor.get();
        AnchorProperty.Anchor last = form.anchor.getLast();
        Matrix4f target = null;
        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, camera, transition);
        float opacity = 1F;

        if (last == null)
        {
            AnchorProperty.Anchor current = value;

            Matrix4f matrix = getEntityMatrix(entities, camera, current, defaultMatrix, transition);

            if (matrix != defaultMatrix)
            {
                target = matrix;
                opacity = 0F;
            }
        }
        else if (value != null)
        {
            Matrix4f matrix = getEntityMatrix(entities, camera, value, defaultMatrix, transition);
            Matrix4f lastMatrix = getEntityMatrix(entities, camera, last, defaultMatrix, transition);

            if (matrix != lastMatrix)
            {
                float factor = form.anchor.getTweenFactorInterpolated(transition);

                target = factor >= 1F ? matrix : Matrices.lerp(lastMatrix, matrix, factor);
                opacity = 1F - factor;
            }
        }

        if (target != null)
        {
            Vector3f v = target.getTranslation(new Vector3f());
            Vector3f v2 = defaultMatrix.getTranslation(new Vector3f());

            position.x += v.x - v2.x;
            position.y += v.y - v2.y;
            position.z += v.z - v2.z;
        }

        BlockPos pos = BlockPos.ofFloored(position.x, position.y + 0.5D, position.z);
        int sky = entity.getWorld().getLightLevel(LightType.SKY, pos);
        int torch = entity.getWorld().getLightLevel(LightType.BLOCK, pos);
        int light = LightmapTextureManager.pack(torch, sky);

        FormRenderingContext formContext = FormRenderingContext
            .set(entity, stack, light, OverlayTexture.DEFAULT_UV, transition)
            .camera(camera)
            .stencilMap(map);

        stack.push();
        MatrixStackUtils.multiply(stack, target == null ? defaultMatrix : target);
        FormUtilsClient.render(form, formContext);
        stack.pop();

        stack.push();

        if (map == null && opacity > 0F)
        {
            stack.translate(position.x - camera.getPos().x, position.y - camera.getPos().y, position.z - camera.getPos().z);

            ModelBlockEntityRenderer.renderShadow(consumers, stack, transition, position.x, position.y, position.z, 0F, 0F, 0F, 0.5F, opacity);
        }

        stack.pop();
    }

    public static Matrix4f getEntityMatrix(List<IEntity> entities, Camera camera, AnchorProperty.Anchor selector, Matrix4f defaultMatrix, float transition)
    {
        int entityIndex = selector.actor;

        if (CollectionUtils.inRange(entities, entityIndex))
        {
            IEntity entity = entities.get(entityIndex);
            Matrix4f basic = new Matrix4f(getMatrixForRenderWithRotation(entity, camera, transition));

            Map<String, Matrix4f> map = new HashMap<>();
            MatrixStack stack = new MatrixStack();

            Form form = entity.getForm();

            if (form != null)
            {
                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, map, "", transition);

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

    /* Film controller */

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

    public List<IEntity> getEntities()
    {
        return this.entities;
    }

    public boolean hasFinished()
    {
        return this.tick >= this.duration;
    }

    public void update()
    {
        this.tick += 1;

        for (int i = 0; i < this.entities.size(); i++)
        {
            if (i == this.exception)
            {
                continue;
            }

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
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (int i = 0; i < this.entities.size(); i++)
        {
            if (i == this.exception)
            {
                continue;
            }

            renderEntity(this.entities, context, this.entities.get(i), null);
        }

        RenderSystem.disableDepthTest();
    }
}