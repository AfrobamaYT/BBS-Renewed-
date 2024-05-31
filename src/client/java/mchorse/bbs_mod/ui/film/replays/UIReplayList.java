package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.ValueForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This GUI is responsible for drawing replays available in the 
 * director thing
 */
public class UIReplayList extends UIList<Replay>
{
    public UIFilmPanel panel;

    public UIReplayList(Consumer<List<Replay>> callback, UIFilmPanel panel)
    {
        super(callback);

        this.panel = panel;

        this.context((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.SCENE_REPLAYS_CONTEXT_ADD, this::addReplay);

            if (this.isSelected())
            {
                menu.action(Icons.DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, this::dupeReplay);
                menu.action(Icons.REMOVE, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE, this::removeReplay);

                if (this.isSelected())
                {
                    menu.action(Icons.POSE, UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM, () -> this.openFormEditor(this.getCurrentFirst().form, false));
                    menu.action(Icons.EDIT, UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM, () -> this.openFormEditor(this.getCurrentFirst().form, true));
                }
            }
        });

        Supplier<Boolean> active = () -> !this.getCurrent().isEmpty();

        this.keys().register(Keys.FORMS_PICK, () ->
        {
            this.openFormEditor(this.getCurrentFirst().form, false);
        }).active(active);
        this.keys().register(Keys.FORMS_EDIT, () ->
        {
            this.openFormEditor(this.getCurrentFirst().form, true);
        }).active(active);
    }

    private void openFormEditor(ValueForm form, boolean editing)
    {
        UIElement target = this.panel;

        if (this.getRoot() != null)
        {
            target = this.getParentContainer();
        }

        UIFormPalette palette = UIFormPalette.open(target, editing, form.get(), (f) ->
        {
            form.set(f);
            this.updateFilmEditor();
        });

        palette.updatable();
    }

    private void addReplay()
    {
        World world = MinecraftClient.getInstance().world;
        Camera camera = this.panel.getCamera();

        BlockHitResult blockHitResult = RayTracing.rayTrace(world, camera, 64F);
        Vec3d p = blockHitResult.getPos();
        Vector3d position = new Vector3d(p.x, p.y, p.z);

        if (blockHitResult.getType() == HitResult.Type.MISS)
        {
            position.set(camera.getLookDirection()).mul(5F).add(camera.position);
        }

        this.addReplay(position, camera.rotation.x, camera.rotation.y + MathUtils.PI);
    }

    public void addReplay(Vector3d position, float pitch, float yaw)
    {
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        replay.keyframes.pitch.insert(0, pitch);
        replay.keyframes.yaw.insert(0, yaw);
        replay.keyframes.headYaw.insert(0, yaw);
        replay.keyframes.bodyYaw.insert(0, yaw);

        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false);
    }

    private void updateFilmEditor()
    {
        this.panel.getController().createEntities();
        this.panel.replayEditor.updateChannelsList();
    }

    private void dupeReplay()
    {
        if (this.isDeselected())
        {
            return;
        }

        Replay currentFirst = this.getCurrentFirst();
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        replay.copy(currentFirst);

        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();
    }

    private void removeReplay()
    {
        if (this.isDeselected())
        {
            return;
        }

        Film film = this.panel.getData();
        int index = this.getIndex();

        film.replays.remove(this.getCurrentFirst());

        int size = this.list.size();
        index = MathUtils.clamp(index, 0, size - 1);

        this.update();
        this.panel.replayEditor.setReplay(size == 0 ? null : this.list.get(index));
        this.updateFilmEditor();
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, Colors.A100);

        if (this.getList().size() < 3)
        {
            UIDataUtils.renderRightClickHere(context, this.area);
        }

        super.render(context);
    }

    @Override
    protected String elementToString(UIContext context, int i, Replay element)
    {
        Form form = element.form.get();

        return form == null ? "-" : context.batcher.getFont().limitToWidth(form.getIdOrName(), this.area.w - 20);
    }

    @Override
    protected void renderElementPart(UIContext context, Replay element, int i, int x, int y, boolean hover, boolean selected)
    {
        super.renderElementPart(context, element, i, x, y, hover, selected);

        Form form = element.form.get();

        if (form != null)
        {
            x += this.area.w - 30;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);

            context.batcher.unclip(context);
        }
    }
}