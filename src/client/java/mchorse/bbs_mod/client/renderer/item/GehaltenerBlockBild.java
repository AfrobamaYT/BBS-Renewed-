package mchorse.bbs_mod.client.renderer.item;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.replays.HandAusHotbar;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** The actual held-form draw, before its own transform, for instance-based viewport picking. */
public final class GehaltenerBlockBild
{
    public record Bild(Replay halter, boolean nebenhand, ItemStack stack, Form form, IEntity entity,
        Matrix4f pose, Matrix3f normal, Matrix4f modelView, Matrix4f projektion, Camera kamera,
        FormRenderType art, int licht, int overlay, float transition, long zeit)
    {}

    private record Hand(String halter, boolean nebenhand) {}
    private static final Map<ModelBlockItemRenderer.Item, WeakReference<ItemStack>> STACKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Hand, Bild> BILDER = new LinkedHashMap<>();
    private static boolean angemeldet;
    private static Field handProjektion;
    private static Film egoFilm;
    private static IEntity egoEntity;
    private static int egoIndex = -1;

    public static void merken(ModelBlockItemRenderer.Item item, ItemStack stack)
    {
        STACKS.put(item, new WeakReference<>(stack));
        if (!angemeldet)
        {
            angemeldet = true;
            LevelRenderEvents.START_MAIN.register(context ->
            {
                if (!com.afrobama.blendertransform.ZweitKamera.aktiv() && !BBSRendering.isIrisShadowPass())
                {
                    BILDER.clear();
                }
            });
        }
    }

    public static UIFilmPanel panel()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        return dashboard != null && UIScreen.getCurrentMenu() == dashboard
            && dashboard.panels.panel instanceof UIFilmPanel panel && panel.getData() != null
            && panel.preview.canBeSeen() ? panel : null;
    }

    public static ItemStack hand(Replay halter, int tick, boolean nebenhand)
    {
        ItemStack stack = nebenhand ? halter.keyframes.offHand.interpolate(tick, ItemStack.EMPTY)
            : HandAusHotbar.hand(halter.keyframes, tick);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public static void erfassen(ModelBlockItemRenderer.Item item, ItemDisplayContext mode, Form form, FormRenderingContext context)
    {
        UIFilmPanel panel = panel();
        if (panel == null || GehaltenerBlockPicker.zeichnet() || com.afrobama.blendertransform.ZweitKamera.aktiv()
            || BBSRendering.isIrisShadowPass())
        {
            return;
        }
        boolean ego = mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean nebenhand = mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!ego && mode != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && mode != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
        {
            return;
        }
        Replay halter;

        if (ego)
        {
            IEntity entity = TrackerClientClip.currentEntity;
            if (entity == null)
            {
                return;
            }
            List<Replay> replays = panel.getData().replays.getList();
            if (egoFilm != panel.getData() || egoEntity != entity || egoIndex < 0
                || egoIndex >= replays.size() || panel.getController().getEntities().get(egoIndex) != entity)
            {
                egoFilm = panel.getData();
                egoEntity = entity;
                egoIndex = -1;
                for (IntObjectMap.PrimitiveEntry<IEntity> entry : panel.getController().getEntities().entries())
                {
                    if (entry.value() == entity)
                    {
                        egoIndex = entry.key();
                        break;
                    }
                }
            }
            if (egoIndex < 0 || egoIndex >= replays.size())
            {
                return;
            }
            halter = replays.get(egoIndex);
        }
        else
        {
            FilmControllerContext film = FilmControllerContext.instance;
            if (!BBSRendering.isRenderingWorld() || film.entities != panel.getController().getEntities()
                || film.entity == null || film.replay == null || film.map != null
                || !enthaelt(film.entity.getForm(), FormUtilsClient.getCurrentForm()))
            {
                return;
            }
            halter = film.replay;
        }

        WeakReference<ItemStack> ref = STACKS.get(item);
        ItemStack stack = ref == null ? null : ref.get();
        if (stack == null || !ItemStack.isSameItemSameComponents(stack, hand(halter, panel.getCursor(), nebenhand)))
        {
            return;
        }
        Matrix4f projektion = ego ? handProjektion() : new Matrix4f(FilmControllerContext.instance.camera.projectionMatrix);
        Camera kamera = new Camera();
        kamera.copy(context.camera);
        BILDER.put(new Hand(halter.uuid.get(), nebenhand), new Bild(halter, nebenhand, stack, form, context.entity,
            new Matrix4f(context.stack.last().pose()), new Matrix3f(context.stack.last().normal()),
            new Matrix4f(RenderSystem.getModelViewMatrixCopy()), projektion, kamera, context.type,
            context.light, context.overlay, context.transition, System.nanoTime()));
    }

    public static List<Bild> bilder()
    {
        long jetzt = System.nanoTime();
        List<Bild> bilder = new ArrayList<>();
        for (Bild bild : BILDER.values())
        {
            if (jetzt - bild.zeit < 1_000_000_000L)
            {
                bilder.add(bild);
            }
        }
        return bilder;
    }

    private static boolean enthaelt(Form wurzel, Form gesucht)
    {
        if (wurzel == null || gesucht == null)
        {
            return false;
        }
        if (wurzel == gesucht)
        {
            return true;
        }
        for (BodyPart teil : wurzel.parts.getAllTyped())
        {
            if (enthaelt(teil.getForm(), gesucht))
            {
                return true;
            }
        }
        return false;
    }

    private static Matrix4f handProjektion()
    {
        try
        {
            if (handProjektion == null)
            {
                handProjektion = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("hudProjection");
                handProjektion.setAccessible(true);
            }
            return ((Projection) handProjektion.get(Minecraft.getInstance().gameRenderer)).getMatrix(new Matrix4f());
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Cannot read the actual hand projection for model-block picking", e);
        }
    }

    private GehaltenerBlockBild()
    {}
}
