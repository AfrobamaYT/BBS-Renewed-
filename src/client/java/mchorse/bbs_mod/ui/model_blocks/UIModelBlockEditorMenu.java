package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.OrbitDistanceCamera;
import mchorse.bbs_mod.camera.controller.OrbitCameraController;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIModelBlockEditorMenu extends UIBaseMenu
{
    private static int lastSection;

    public UIElement iconBar;
    public UIIcon thirdPerson;
    public UIIcon firstPerson;
    public UIIcon inventory;
    public UIIcon gun;
    public UIIcon projectile;
    public UIIcon impact;
    public UIIcon commands;

    public Map<UIElement, UIIcon> sections = new HashMap<>();
    public UIElement currentSection;
    public UIElement sectionTp;
    public UIElement sectionFp;
    public UIElement sectionInventory;
    public UIElement sectionGun;
    public UIElement sectionProjectile;
    public UIElement sectionImpact;
    public UIElement sectionCommands;

    /* Data */
    private ModelProperties properties;
    private GunProperties gunProperties;

    /* Camera */
    private UIOrbitCamera uiOrbitCamera;
    private OrbitCameraController orbitCameraController;

    public UIModelBlockEditorMenu(ModelProperties properties)
    {
        this.properties = properties;

        if (properties instanceof GunProperties gunProperties)
        {
            this.gunProperties = gunProperties;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        OrbitDistanceCamera orbit = new OrbitDistanceCamera();

        orbit.distance.setX(14);
        this.uiOrbitCamera = new UIOrbitCamera();
        this.uiOrbitCamera.setControl(true);
        this.uiOrbitCamera.orbit = orbit;
        this.orbitCameraController = new OrbitCameraController(this.uiOrbitCamera.orbit);
        this.orbitCameraController.camera.position.set(player.getPos().x, player.getPos().y + 1D, player.getPos().z);
        this.orbitCameraController.camera.rotation.set(0, MathUtils.toRad(player.bodyYaw), 0);

        /* Initiate sections */
        this.sectionTp = this.createTransform(
            this.properties.getTransformThirdPerson(),
            () -> this.properties.getFormThirdPerson(),
            (f) -> this.properties.setFormThirdPerson(f)
        );

        this.sectionFp = this.createTransform(
            this.properties.getTransformFirstPerson(),
            () -> this.properties.getFormFirstPerson(),
            (f) -> this.properties.setFormFirstPerson(f)
        );

        this.sectionInventory = this.createTransform(
            this.properties.getTransformInventory(),
            () -> this.properties.getFormInventory(),
            (f) -> this.properties.setFormInventory(f)
        );

        GunProperties gun = this.gunProperties;

        if (gun != null)
        {
            /* Gun properties */
            UIToggle launch = new UIToggle(IKey.raw("Launch"), (b) -> gun.launch = b.getValue());
            UITrackpad launchPower = new UITrackpad((v) -> gun.launchPower = v.floatValue());
            UIToggle launchAdditive = new UIToggle(IKey.raw("Additive"), (b) -> gun.launchAdditive = b.getValue());
            UITrackpad scatterX = new UITrackpad((v) -> gun.scatterX = v.floatValue());
            UITrackpad scatterY = new UITrackpad((v) -> gun.scatterY = v.floatValue());
            UITrackpad projectiles = new UITrackpad((v) -> gun.projectiles = v.intValue()).limit(1).integer();

            launch.setValue(gun.launch);
            launchPower.setValue(gun.launchPower);
            launchAdditive.setValue(gun.launchAdditive);
            scatterX.setValue(gun.scatterX);
            scatterX.tooltip(IKey.raw("Horizontal scatter"));
            scatterY.setValue(gun.scatterY);
            scatterY.tooltip(IKey.raw("Vertical scatter"));
            projectiles.setValue(gun.projectiles);
            projectiles.limit(1).integer();

            this.sectionGun = UI.scrollView(5, 10,
                launch, launchPower, launchAdditive,
                UI.label(IKey.raw("Scatter")).background().marginTop(6), UI.row(scatterX, scatterY),
                UI.label(IKey.raw("Projectiles")).background().marginTop(6), projectiles
            );
            this.sectionGun.relative(this.viewport).x(1F).w(200).h(1F).anchorX(1F);
            this.gun = new UIIcon(Icons.GEAR, (b) -> this.setSection(this.sectionGun));
            this.gun.tooltip(IKey.raw("Gun shooting options"));

            /* Projectile */
            UINestedEdit projectileForm = new UINestedEdit((edit) -> UIFormPalette.open(this.main, edit, gun.projectileForm, (f) ->
            {
                gun.projectileForm = FormUtils.copy(f);
                this.sectionProjectile.getChildren(UINestedEdit.class).get(0).setForm(f);
            }));
            UIPropTransform projectileTransform = new UIPropTransform();
            UIToggle useTarget = new UIToggle(IKey.raw("Use target"), (b) -> gun.useTarget = b.getValue());
            UITrackpad lifeSpan = new UITrackpad((v) -> gun.lifeSpan = v.intValue());
            UITrackpad speed = new UITrackpad((v) -> gun.speed = v.floatValue());
            UITrackpad friction = new UITrackpad((v) -> gun.friction = v.floatValue());
            UITrackpad gravity = new UITrackpad((v) -> gun.gravity = v.floatValue());
            UIToggle yaw = new UIToggle(IKey.raw("Yaw"), (b) -> gun.yaw = b.getValue());
            UIToggle pitch = new UIToggle(IKey.raw("Pitch"), (b) -> gun.pitch = b.getValue());
            UITrackpad fadeIn = new UITrackpad((v) -> gun.fadeIn = v.intValue());
            UITrackpad fadeOut = new UITrackpad((v) -> gun.fadeOut = v.intValue());

            projectileForm.setForm(gun.projectileForm);
            projectileTransform.setTransform(gun.projectileTransform);
            useTarget.setValue(gun.useTarget);
            lifeSpan.setValue(gun.lifeSpan);
            speed.setValue(gun.speed);
            friction.setValue(gun.friction);
            gravity.setValue(gun.gravity);
            yaw.setValue(gun.yaw);
            yaw.tooltip(IKey.raw("Horizontal rotation"));
            pitch.setValue(gun.pitch);
            pitch.tooltip(IKey.raw("Vertical rotation"));
            fadeIn.setValue(gun.fadeIn);
            fadeIn.limit(0).integer().tooltip(IKey.raw("Fade in"));
            fadeOut.setValue(gun.fadeOut);
            fadeOut.limit(0).integer().tooltip(IKey.raw("Fade out"));

            this.sectionProjectile = UI.scrollView(5, 10,
                UI.label(IKey.raw("Projectile form")).background(), projectileForm,
                UI.label(IKey.raw("Projectile transform")).background().marginTop(6), projectileTransform,
                useTarget.marginTop(6),
                UI.label(IKey.raw("Life span")).background().marginTop(6), lifeSpan,
                UI.label(IKey.raw("Speed")).background().marginTop(6), speed,
                UI.label(IKey.raw("Friction")).background(), friction,
                UI.label(IKey.raw("Gravity")).background(), gravity,
                UI.label(IKey.raw("Rotations")).background().marginTop(6), UI.row(yaw, pitch),
                UI.label(IKey.raw("Scale fading")).background().marginTop(6), UI.row(fadeIn, fadeOut)
            );
            this.sectionProjectile.relative(this.viewport).x(1F).w(200).h(1F).anchorX(1F);
            this.projectile = new UIIcon(Icons.BULLET, (b) -> this.setSection(this.sectionProjectile));
            this.projectile.tooltip(IKey.raw("Projectile options"));

            /* Impact */
            UINestedEdit impactForm = new UINestedEdit((edit) -> UIFormPalette.open(this.main, edit, gun.impactForm, (f) ->
            {
                gun.impactForm = FormUtils.copy(f);
                this.sectionImpact.getChildren(UINestedEdit.class).get(0).setForm(f);
            }));
            UITrackpad bounceHits = new UITrackpad((v) -> gun.bounces = v.intValue());
            UITrackpad bounceDamping = new UITrackpad((v) -> gun.bounceDamping = v.floatValue());
            UIToggle vanish = new UIToggle(IKey.raw("Vanish"), (b) -> gun.vanish = b.getValue());
            UITrackpad damage = new UITrackpad((v) -> gun.damage = v.floatValue());
            UITrackpad knockback = new UITrackpad((v) -> gun.knockback = v.floatValue());
            UIToggle collideBlocks = new UIToggle(IKey.raw("Blocks"), (b) -> gun.collideBlocks = b.getValue());
            UIToggle collideEntities = new UIToggle(IKey.raw("Entities"), (b) -> gun.collideEntities = b.getValue());

            impactForm.setForm(gun.impactForm);
            bounceHits.setValue(gun.bounces);
            bounceDamping.setValue(gun.bounceDamping);
            vanish.setValue(gun.vanish);
            damage.setValue(gun.damage);
            knockback.setValue(gun.knockback);
            collideBlocks.setValue(gun.collideBlocks);
            collideEntities.setValue(gun.collideEntities);

            this.sectionImpact = UI.scrollView(5, 10,
                UI.label(IKey.raw("Impact form")).background(), impactForm,
                UI.label(IKey.raw("Bounces")).background().marginTop(6), bounceHits,
                UI.label(IKey.raw("Bounce damping")).background(), bounceDamping,
                vanish.marginTop(6),
                UI.label(IKey.raw("Damage")).background().marginTop(6), damage,
                UI.label(IKey.raw("Knockback")).background().marginTop(6), knockback,
                UI.label(IKey.raw("Collision")).background().marginTop(6), UI.row(collideBlocks, collideEntities)
            );
            this.sectionImpact.relative(this.viewport).x(1F).w(200).h(1F).anchorX(1F);
            this.impact = new UIIcon(Icons.DOWNLOAD, (b) -> this.setSection(this.sectionImpact));
            this.impact.tooltip(IKey.raw("Impact options"));

            /* Commands */
            UITextbox cmdFiring = new UITextbox(10000, (t) -> gun.cmdFiring = t);
            UITextbox cmdImpact = new UITextbox(10000, (t) -> gun.cmdImpact = t);
            UITextbox cmdVanish = new UITextbox(10000, (t) -> gun.cmdVanish = t);
            UITextbox cmdTicking = new UITextbox(10000, (t) -> gun.cmdTicking = t);
            UITrackpad ticking = new UITrackpad((v) -> gun.ticking = v.intValue());

            cmdFiring.setText(gun.cmdFiring);
            cmdImpact.setText(gun.cmdImpact);
            cmdVanish.setText(gun.cmdVanish);
            cmdTicking.setText(gun.cmdTicking);
            ticking.limit(0).integer().setValue(gun.ticking);
            ticking.tooltip(IKey.raw("How frequently ticking command will be executed. 0 means never."));

            this.sectionCommands = UI.scrollView(5, 10,
                UI.label(IKey.raw("When fired")).background(), cmdFiring,
                UI.label(IKey.raw("On impact")).background(), cmdImpact,
                UI.label(IKey.raw("On death")).background(), cmdVanish,
                UI.label(IKey.raw("On tick")).background(), cmdTicking,
                ticking
            );
            this.sectionCommands.relative(this.viewport).x(1F).w(200).h(1F).anchorX(1F);
            this.commands = new UIIcon(Icons.CONSOLE, (b) -> this.setSection(this.sectionCommands));
            this.commands.tooltip(IKey.raw("Commands"));
        }

        this.thirdPerson = new UIIcon(Icons.POSE, (b) -> this.setSection(this.sectionTp));
        this.thirdPerson.tooltip(UIKeys.MODEL_BLOCKS_TRANSFORM_THIRD_PERSON);
        this.firstPerson = new UIIcon(Icons.LIMB, (b) -> this.setSection(this.sectionFp));
        this.firstPerson.tooltip(UIKeys.MODEL_BLOCKS_TRANSFORM_FIRST_PERSON);
        this.inventory = new UIIcon(Icons.SPHERE, (b) -> this.setSection(this.sectionInventory));
        this.inventory.tooltip(UIKeys.MODEL_BLOCKS_TRANSFORM_INVENTORY);

        this.sections.put(this.sectionTp, this.thirdPerson);
        this.sections.put(this.sectionFp, this.firstPerson);
        this.sections.put(this.sectionInventory, this.inventory);

        this.iconBar = UI.row(0, this.thirdPerson, this.firstPerson, this.inventory, this.gun, this.projectile, this.impact, this.commands);
        this.iconBar.row().resize();
        this.iconBar.relative(this.viewport).x(0.5F).h(20).anchor(0.5F, 0F);

        this.main.add(this.uiOrbitCamera, this.iconBar);
        this.main.add(this.sectionTp, this.sectionFp, this.sectionInventory);

        if (gun != null)
        {
            this.sections.put(this.sectionGun, this.gun);
            this.sections.put(this.sectionProjectile, this.projectile);
            this.sections.put(this.sectionImpact, this.impact);
            this.sections.put(this.sectionCommands, this.commands);

            this.main.add(this.sectionGun, this.sectionProjectile, this.sectionImpact, this.sectionCommands);
        }

        int index = Math.min(lastSection, this.sections.size() - 1);

        this.setSection(CollectionUtils.getKey(this.sections, (UIIcon) this.iconBar.getChildren().get(index)));
    }

    private UIElement createTransform(Transform transform, Supplier<Form> formSupplier, Consumer<Form> formConsumer)
    {
        UIElement section = UI.column();
        UINestedEdit uiPickEdit = new UINestedEdit((edit) -> UIFormPalette.open(this.main, edit, formSupplier.get(), (f) ->
        {
            formConsumer.accept(FormUtils.copy(f));
            section.getChildren(UINestedEdit.class).get(0).setForm(f);
        }));
        UIPropTransform uiTransform = new UIPropTransform();

        uiPickEdit.setForm(formSupplier.get());
        uiTransform.enableHotkeys().h(95);
        uiTransform.setTransform(transform);

        section.add(uiPickEdit, uiTransform);
        section.relative(this.viewport).x(1F, -10).y(0.5F).w(200).anchor(1F, 0.5F);

        return section;
    }

    @Override
    public boolean canHideHUD()
    {
        return false;
    }

    @Override
    public void onClose(UIBaseMenu nextMenu)
    {
        super.onClose(nextMenu);

        lastSection = this.iconBar.getChildren().indexOf(this.sections.get(this.currentSection));

        BBSModClient.getCameraController().remove(this.orbitCameraController);
        ClientNetwork.sendModelBlockTransforms(this.properties.toData());
    }

    private void setSection(UIElement element)
    {
        if (element != this.currentSection)
        {
            this.uiOrbitCamera.setEnabled(element == this.sectionTp);

            if (element == this.sectionTp)
            {
                MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                BBSModClient.getCameraController().add(this.orbitCameraController);
            }
            else
            {
                MinecraftClient.getInstance().options.setPerspective(Perspective.FIRST_PERSON);
                BBSModClient.getCameraController().remove(this.orbitCameraController);
            }
        }

        for (UIElement uiElement : this.sections.keySet())
        {
            uiElement.setVisible(false);
        }

        element.setVisible(true);

        this.currentSection = element;
    }

    @Override
    protected void preRenderMenu(UIRenderingContext context)
    {
        super.preRenderMenu(context);
        context.batcher.gradientVBox(0, 0, this.width, 20, Colors.A75, 0);

        UIIcon icon = this.sections.get(this.currentSection);

        if (icon != null)
        {
            this.renderHighlight(context.batcher, icon.area);
        }
    }

    private void renderHighlight(Batcher2D batcher, Area area)
    {
        int color = BBSSettings.primaryColor.get();

        batcher.box(area.x, area.y, area.ex(), area.y + 2, Colors.A100 | color);
        batcher.gradientVBox(area.x, area.y + 2, area.ex(), area.ey(), Colors.A50 | color, color);
    }
}