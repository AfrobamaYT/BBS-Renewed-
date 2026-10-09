package mchorse.bbs_mod.forms.renderers;

import net.minecraft.client.renderer.RenderPipelines;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
/* ⛔ P3-26.2-AUS: import von mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug entfernt —
 * die klasse ist im ausschluss-block von build.gradle stillgelegt. Ihre beiden aufrufe
 * stehen weiter unten (renderModel / updateStencilMap) je mit begruendung. */
import mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class ModelFormRenderer extends FormRenderer<ModelForm> implements ITickable
{
    private static Matrix4f uiMatrix = new Matrix4f();

    public ModelForm getForm()
    {
        return this.form;
    }

    private MatrixCache bones = new MatrixCache();

    /**
     * Der Grundindex DIESER Form im Auswahlpuffer, gemerkt beim Fuellen.
     *
     * <p>Ohne Initialisierer (0 = noch nicht gefuellt) — dann ueberlebt ein Hotswap dieser
     * Klasse, ohne dass ein {@code <clinit>} nachlaufen muesste.</p>
     */
    private int stencilBasis;

    private ActionsConfig lastConfigs;
    private IAnimator animator;
    private ModelInstance lastModel;
    private boolean ikAppliedThisRender;
    private boolean physicsAppliedThisRender;
    private boolean constraintsAppliedThisRender;
    private boolean renderingArm;

    private IEntity entity = new StubEntity();

    @Override
    protected void applyTransforms(PoseStack stack, boolean origin, float transition)
    {
        super.applyTransforms(stack, origin, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            Vector3f scale = model.getScale();

            stack.scale(scale.x, scale.y, scale.z);
        }
    }

    @Override
    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        super.applyTransforms(matrix, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            Vector3f scale = model.getScale();

            matrix.scale(scale.x, scale.y, scale.z);
        }
    }

    public static Matrix4f getUIMatrix(UIContext context, int x1, int y1, int x2, int y2)
    {
        float scale = (y2 - y1) / 2.5F;
        int x = x1 + (x2 - x1) / 2;
        float y = y1 + (y2 - y1) * 0.85F;
        float angle = MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

        if (BBSSettings.freezeModels.get())
        {
            angle = -MathUtils.PI + MathUtils.PI / 8;
        }

        uiMatrix.identity();
        uiMatrix.translate(x, y, 40);
        uiMatrix.scale(scale, -scale, scale);
        uiMatrix.rotateX(MathUtils.PI / 8);
        uiMatrix.rotateY(angle);

        return uiMatrix;
    }

    public static ModelInstance getModel(ModelForm form)
    {
        ModelInstance model = BBSModClient.getModels().getModel(form.model.get());

        /* Importierte IK-Ketten (arm_ik) landen beim Bind als Vorgabe in form.ik —
         * ein Form ohne eigene IK-Config bekommt das Rig aus der Modelldatei. */
        mchorse.bbs_mod.cubic.ik.ModelIKEmbedded.uebernehmen(form, model);

        /* Der importierte Constraint-Graph (rig.json) — ohne ihn sind die Controls Zierrat. */
        if (model != null && model.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj)
        {
            mchorse.bbs_mod.cubic.ik.RigMechanik.installieren(bobj, form.model.get());
        }

        return model;
    }

    public ModelFormRenderer(ModelForm form)
    {
        super(form);
    }

    public IAnimator getAnimator()
    {
        return this.animator;
    }

    public ModelInstance getModel()
    {
        return getModel(this.form);
    }

    public Pose getPose()
    {
        Pose pose = this.form.pose.get().copy();
        Pose overlay = this.form.poseOverlay.get();

        this.applyPose(pose, overlay);

        for (ValuePose newPose : this.form.additionalOverlays)
        {
            this.applyPose(pose, newPose.get());
        }

        return pose;
    }

    private void applyPose(Pose targetPose, Pose pose)
    {
        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform poseTransform = targetPose.get(entry.getKey());
            PoseTransform value = entry.getValue();

            if (!Operation.equals(value.fix, 0))
            {
                poseTransform.translate.lerp(value.translate, value.fix);
                poseTransform.scale.lerp(value.scale, value.fix);
                poseTransform.lerpRotation(value, value.fix);
            }
            else
            {
                poseTransform.translate.add(value.translate);
                poseTransform.scale.add(value.scale).sub(1, 1, 1);
                poseTransform.addRotation(value);
            }
        }
    }

    public void resetAnimator()
    {
        this.animator = null;
        this.lastModel = null;
    }

    /**
     * The channels phase of the bone pipeline (rest &rarr; actions &rarr; pose): resets every bone
     * to its bind pose, applies the animator's actions, then the form's pose stack. After this the
     * channels are the FK truth; the constraint stages (IK &rarr; physics &rarr; limits) run on top
     * of it separately (render: the apply*Once trio; matrix capture: its explicit IK solve) and
     * write only evaluated orientations, never the channels.
     */
    private void evaluateChannels(IEntity entity, ModelInstance model, float transition)
    {
        model.model.resetPose();
        this.animator.applyActions(entity, model, transition);
        model.model.applyPose(this.getPose());
    }

    public void ensureAnimator(float transition)
    {
        ModelInstance model = this.getModel();
        ActionsConfig actionsConfig = this.form.actions.get();

        if (model == null || this.lastModel == model)
        {
            /* Update the config */
            if (this.animator != null && !Objects.equals(actionsConfig, this.lastConfigs))
            {
                this.animator.setup(model, actionsConfig, true);

                this.lastConfigs = new ActionsConfig();
                this.lastConfigs.copy(actionsConfig);
            }

            return;
        }

        this.animator = model.isProcedural() ? new ProceduralAnimator() : new Animator();
        this.animator.setup(model, actionsConfig, false);

        this.lastConfigs = new ActionsConfig();
        this.lastConfigs.copy(actionsConfig);
        this.lastModel = model;
    }

    @Override
    public List<String> getBones()
    {
        ModelInstance model = this.getModel();

        if (model == null)
        {
            return Collections.emptyList();
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
        bones.removeIf((bone) -> PoseBones.isHidden(model.getDisabledBones(), bone));

        return bones;
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            PoseStack stack = context.batcher.pose();

            stack.pushPose();

            Matrix4f uiMatrix = getUIMatrix(context, x1, y1, x2, y2);

            this.applyTransforms(uiMatrix, context.getTransition());

            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = Color.white();
            Color formColor = this.form.color.get();
            float scale = this.form.uiScale.get() * model.getUiScale();

            this.evaluateChannels(null, model, context.getTransition());

            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            BBSModClient.getTextures().bindTexture(texture);

            /* 26.2: depthFunc(GL_LEQUAL) ist ersatzlos entfallen. Der Tiefentest ist kein
             * umschaltbarer Zustand mehr, er steht in der Pipeline des Draws, der danach kommt:
             * der Modell-Draw unten laeuft ueber ModelVAOPipeline (alle drei Varianten tragen
             * DepthStencilState(GREATER_THAN_OR_EQUAL, true)) bzw. - ohne VAO und unter Iris -
             * ueber die Pipeline des Entity-RenderTypes. GREATER_THAN_OR_EQUAL ist dabei das
             * Gegenstueck zu GL_LEQUAL, nicht LESS_THAN_OR_EQUAL: 26.2 rechnet die Tiefe umgekehrt
             * (naeher = groesserer Wert, Tiefenpuffer wird auf 0.0 geloescht). */

            Supplier<RenderPipeline> mainShader = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld()) || !model.isVAORendered()
                ? () -> RenderPipelines.ENTITY_TRANSLUCENT_CULL
                : BBSShaders::getModel;

            boolean additive = this.form.additiveColor.get();
            this.renderModel(this.entity, mainShader, stack, model, net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY, contextColor, formColor, additive, true, null, context.getTransition(), null);

            /* Render body parts */
            stack.pushPose();
            stack.last().normal().getScale(Vectors.EMPTY_3F);
            stack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            this.renderBodyParts(new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.entity, stack, net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY, context.getTransition())
                .inUI());

            stack.popPose();
            stack.popPose();

            /* 26.2: das abschliessende depthFunc(GL_ALWAYS) ist ersatzlos entfallen. Es stellte den
             * UI-Vorgabezustand fuer alles her, was nach dem Modell noch in die Oberflaeche zeichnet
             * - und der GUI-Raum hat keine Tiefe mehr: Batcher2D reicht seine Zeichnungen als
             * GuiElementRenderState mit RenderPipelines.GUI bzw. GUI_TEXTURED ein, die Ueberdeckung
             * entscheidet die Stapellage (GuiGraphicsExtractor.nextStratum()), kein Tiefenvergleich.
             * Einen globalen Zustand, den man zuruecksetzen koennte, gibt es nicht mehr. */
        }
    }

    private void renderModel(IEntity target, Supplier<RenderPipeline> program, PoseStack stack, ModelInstance model, int light, int overlay, Color contextColor, Color formColor, boolean additive, boolean ui, StencilMap stencilMap, float transition, PoseStack world)
    {
        this.ikAppliedThisRender = false;
        this.physicsAppliedThisRender = false;
        this.constraintsAppliedThisRender = false;

        Color finalColor = contextColor.copy();
        FormColorBlend.BlendMode blendMode = additive ? FormColorBlend.BlendMode.BRIGHTEN : FormColorBlend.BlendMode.MULTIPLY;
        FormColorBlend.blend(finalColor, formColor, blendMode);

        /* 26.2: disableCull/enableBlend/defaultBlendFunc sind ersatzlos entfallen — Blend und
         * Cull stehen in der Pipeline und werden an der Zeichenstelle GEWAEHLT (Modelle ohne
         * Culling: DURCHSICHTIG_BEIDSEITIG, sonst DURCHSICHTIG; beide TRANSLUCENT). */
        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;

        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        PoseStack newStack = new PoseStack();

        MatrixStackUtils.multiply(newStack, stack.last().pose());
        newStack.last().normal().set(stack.last().normal());

        if (ui)
        {
            newStack.last().normal().getScale(Vectors.EMPTY_3F);
            newStack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);
        }

        /* Strictly the world frame: it's what places the model in the world for the simulating subsystems
         * (bone physics resolves gravity, wind and its collisions against it), so falling back to the render
         * stack when there is no world stack — the first person arm — resolved them against the camera
         * instead, and gravity pulled toward the bottom of the screen. Without a world frame there is no
         * honest answer, so they run model-local, as they do in the UI. */
        Matrix4f baseTransform = ui || world == null ? null : new Matrix4f(world.last().pose());

        this.applyIKOnce(model, baseTransform);
        this.applyPhysicsOnce(target, model, transition, baseTransform);
        this.applyConstraintsOnce(model);

        /* Default texture for materials without their own: the form's texture override, else the
         * model's default. Per-material textures (folder defaults now, animation tracks later)
         * layer on top via the resolver. */
        Link defaultTexture = this.form.texture.get();

        if (defaultTexture == null)
        {
            defaultTexture = model.getTexture();
        }

        final Link resolvedDefault = defaultTexture;

        /* A model with at most one material ignores the material system entirely: a single texture
         * (form.texture, else the model's base texture) covers the whole model, regardless of any
         * per-material folder/Kd default, editor pick, or animation track. Only with multiple materials
         * is the Default ambiguous - it's hidden in the editor then and must not affect them here either,
         * so they fall back to the model base texture. */
        final boolean ignoreMaterials = model.materials.size() <= 1;
        final Link materialFallback = ignoreMaterials ? resolvedDefault : model.getTexture();

        /* Die in updateStencilMap gemerkte Basis DIESER Form unmittelbar vor dem Zeichnen
         * wiederherstellen -- ModelInstance liest sie hier by value in den Modell-Block. */
        if (stencilMap != null)
        {
            stencilMap.aktuelleBasis = this.stencilBasis;
        }

        model.render(newStack, program, finalColor, light, overlay, stencilMap, this.form.shapeKeys.get(), (material) ->
        {
            if (ignoreMaterials)
            {
                return resolvedDefault;
            }

            /* Resolution order: animated per-material track > editor-picked static per-material
             * texture > the material's loaded default (folder/Kd) > the model base texture. */
            Link override = this.form.materialTextureOverrides.get(material);

            if (override != null)
            {
                return override;
            }

            Link picked = this.form.materialTextures.getLink(material);

            if (picked != null)
            {
                return picked;
            }

            return model.getMaterialTexture(material, materialFallback);
        });

        if (stencilMap == null && !this.renderingArm && this.form != null && this.form.ik.get() instanceof MapType ikMap)
        {
            ModelIKDebug.render(newStack, model.model, ikMap, "", this.form.model.get());
        }

        if (stencilMap == null && !this.renderingArm && this.form != null && this.form.physics.get() instanceof MapType physicsMap)
        {
            ModelPhysicsDebug.render(newStack, model.model, physicsMap, target.getAge(), "");
        }


        /* 26.2: das disableBlend()/enableCull()-Paar hier ist ersatzlos entfallen. Beide stellten nur
         * den globalen Vorgabezustand fuer das her, was DANACH zeichnet - und das sind die Vanilla-
         * Ruestungs- und Item-Renderer unten, die Blend und Cull aus der Pipeline ihres RenderType
         * mitbringen (RenderType#pipeline()). Einen globalen Schalter, den man zurueckstellen
         * koennte, gibt es nicht mehr.
         * Ihre Gegenstuecke oben in dieser Methode richteten den Zustand dagegen FUER den Modell-Draw
         * ein, der ueber VAO/eigenes Programm laeuft und keinen RenderType hat (ModelInstance#render).
         * Sie sind seit dem Umbau des Zeichenwegs ebenfalls entfallen: ihr Zustand steht in
         * ModelVAOPipeline (siehe den Kommentar oben, wo sie standen). */

        /* Render items */
        this.captureMatrices(model);

        if (stencilMap == null)
        {
            this.renderItems(target, model, stack, EquipmentSlot.MAINHAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, model.getItemsMain(), finalColor, overlay, light);
            this.renderItems(target, model, stack, EquipmentSlot.OFFHAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, model.getItemsOff(), finalColor, overlay, light);

            for (Map.Entry<ArmorType, ArmorSlot> entry : model.getArmorSlots().entrySet())
            {
                this.renderArmor(target, stack, entry.getKey(), entry.getValue(), finalColor, overlay, light);
            }
        }
    }

    private void applyIKOnce(ModelInstance model, Matrix4f baseTransform)
    {
        if (this.ikAppliedThisRender)
        {
            return;
        }

        this.ikAppliedThisRender = true;
        model.form = this.form;

        boolean hasOverrides = baseTransform != null && this.form != null
            && (!this.form.ikTargetOverrides.isEmpty() || !this.form.poleTargetOverrides.isEmpty());

        if (!hasOverrides)
        {
            return;
        }

        Matrix4f inv = new Matrix4f(baseTransform).invert();
        Map<String, Vector3f> local = toModelSpace(this.form.ikTargetOverrides, inv);
        Map<String, Vector3f> poleLocal = toModelSpace(this.form.poleTargetOverrides, inv);

        if (local.isEmpty() && poleLocal.isEmpty())
        {
            return;
        }

    }

    /** World-space target overrides into the model's local space (the space the solver and pivot frames use). */
    private static Map<String, Vector3f> toModelSpace(Map<String, Vector3f> world, Matrix4f inv)
    {
        Map<String, Vector3f> local = new HashMap<>(world.size() * 2);

        for (Map.Entry<String, Vector3f> entry : world.entrySet())
        {
            String key = entry.getKey();
            Vector3f worldPos = entry.getValue();

            if (key == null || key.isEmpty() || worldPos == null)
            {
                continue;
            }

            Vector3f pos = new Vector3f(worldPos);
            inv.transformPosition(pos);
            local.put(key, pos);
        }

        return local;
    }

    private void applyPhysicsOnce(IEntity target, ModelInstance model, float transition, Matrix4f baseTransform)
    {
        if (this.physicsAppliedThisRender)
        {
            return;
        }

        this.physicsAppliedThisRender = true;
        model.lastBaseTransform = baseTransform;
        model.form = this.form;
        ModelPhysicsRuntime.apply(target, model, transition, baseTransform);
    }

    private void applyConstraintsOnce(ModelInstance model)
    {
        if (this.constraintsAppliedThisRender)
        {
            return;
        }

        this.constraintsAppliedThisRender = true;
        ModelConstraintsRuntime.apply(model);
    }

    private void renderArmor(IEntity target, PoseStack stack, ArmorType type, ArmorSlot armorSlot, Color color, int overlay, int light)
    {
        Matrix4f matrix = this.bones.get(armorSlot.group).matrix();

        if (matrix != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            stack.pushPose();
            MatrixStackUtils.multiply(stack, matrix);
            MatrixStackUtils.applyTransform(stack, armorSlot.transform);
            stack.mulPose(Axis.XP.rotationDegrees(180F));

            /* 26.2: der Hijack ist ersatzlos entfallen. Er tat nichts anderes, als je gezeichneter
             * RenderType-Ebene (RenderLayerMixin haengt an RenderType#setupRenderState) den globalen
             * Blend-Schalter anzuschalten. Die Ruestungs-Ebenen, die der Vanilla-Renderer unten selbst
             * waehlt, tragen ihr Blending in ihrer eigenen Pipeline (RenderType#pipeline()).
             * clearRunnables() unten bleibt stehen und laeuft nun ins Leere - harmlos. */

            /* Translucent armor layers ride the deferred sorted pass (see
             * CustomVertexConsumerProvider#draw(RenderLayer)); only reached outside picking. */
            Vector3f armorOrigin = stack.last().pose().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).transformPosition(armorOrigin));

            /* 26.2: der Ruestungs-Zeichner nimmt den SAMMLER, keinen Verbraucher-Anbieter mehr
             * -- er reicht die Modellteile beim Zeichenweg ein statt in einen Puffer zu
             * schreiben (ArmorRenderer.renderArmorSlot traegt die neue Signatur schon).
             * Der Anbieter bleibt trotzdem in der Hand: er haelt den Sammler UND die
             * Umfaerbung, und die Umfaerbung braucht der Item-Zweig weiter unten.
             *
             * ⚠️ Hier ist KEINE Umfaerbung gesetzt (setSubstitute wird nur im Item-Zweig
             * benutzt), also verliert dieser Weg durch den Wechsel nichts. */
            ActorEntityRenderer.armorRenderer.renderArmorSlot(stack, consumers.sammler(), target, type.slot, type, light);
            consumers.draw();
            FormTranslucentQueue.setSortOrigin(null);


            stack.popPose();

            /* 26.2: hier ist nichts mehr zurueckzustellen. Blend und Tiefentest sind keine globalen
             * Zustaende mehr, die der Ruestungs-Draw verstellt haben koennte - jeder Draw bekommt sie
             * aus seiner Pipeline (RenderType#pipeline()). Ersatzlos entfallen. */
        }
    }

    private void renderItems(IEntity target, ModelInstance model, PoseStack stack, EquipmentSlot slot, ItemDisplayContext mode, List<ArmorSlot> items, Color color, int overlay, int light)
    {
        ItemStack itemStack = target.getEquipmentStack(slot);

        if (itemStack != null && itemStack.isEmpty())
        {
            return;
        }

        for (ArmorSlot armorSlot : items)
        {
            Matrix4f matrix = this.bones.get(armorSlot.group).matrix();

            if (matrix != null)
            {
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                stack.pushPose();
                MatrixStackUtils.multiply(stack, matrix);
                stack.mulPose(Axis.XP.rotationDegrees(90F));
                stack.mulPose(Axis.YP.rotationDegrees(180F));
                stack.translate(0F, 0.125F, 0F);
                MatrixStackUtils.applyTransform(stack, armorSlot.transform);

                /* 26.2: der Hijack ist ersatzlos entfallen - gleicher Grund wie in renderArmor: er
                 * schaltete je gezeichneter RenderType-Ebene den globalen Blend-Schalter an. Die
                 * Item-Ebenen, die der Vanilla-Item-Renderer unten waehlt, bringen ihr Blending in
                 * ihrer Pipeline mit (RenderType#pipeline()). */

                /* Translucent item layers (potions, glass blocks in hand) ride the deferred
                 * sorted pass; only reached outside picking. */
                Vector3f itemOrigin = stack.last().pose().getTranslation(new Vector3f());

                FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).transformPosition(itemOrigin));

                consumers.setSubstitute(BBSRendering.getColorConsumer(color));

                /* 26.2: Minecraft.getItemRenderer() und ItemRenderer.renderStatic gibt es nicht
                 * mehr. Beide Aufrufe gehen jetzt ueber graphics/Gegenstandsmodell -- das
                 * Gegenstueck zu graphics/Blockmodell, aus demselben Grund:
                 * ItemStackRenderState.submit(...) gibt KEINEN VertexConsumer heraus, an dem
                 * die Umfaerbung greifen koennte. Wer stumpf darauf umstellt, bekommt einen
                 * sichtbaren Gegenstand OHNE Faerbung -- es uebersetzt, es zeichnet, und die
                 * Faerbung ist lautlos weg. Gegenstandsmodell faengt den Sammler ab und
                 * reicht die Vierecke ueber submitCustomGeometry ein.
                 *
                 * ⚠️ Die Umfaerbung wird DIREKT uebergeben, nicht aus setSubstitute oben
                 * geholt: der Rumpf eines submitCustomGeometry laeuft erst beim Bauen des
                 * Bildes, also lange nach dem setSubstitute(null) weiter unten. Dort abgeholt
                 * waere sie null. Die Zeile mit setSubstitute bleibt trotzdem stehen -- sie
                 * gilt fuer alles andere, was in diesem Abschnitt durch den Anbieter geht.
                 *
                 * ⚠️ Der Linkshaender-Schalter (mode == THIRD_PERSON_LEFT_HAND) entfaellt:
                 * 26.2 leitet ihn aus dem Anzeigekontext selbst ab. Fuer die beiden Kontexte,
                 * die BBS hier reicht, ist das derselbe Wert. */

                /* For some reason, due to Sodium and my color consumer, in some cases items like Trident,
                 * shield, etc. not get rendered, but if in another arm there is another item, it does render...
                 * So, I render a 0 size oak button to circumvent that bug! */
                if (model.model instanceof BOBJModel)
                {
                    stack.pushPose();
                    stack.scale(0F, 0F, 0F);
                    mchorse.bbs_mod.graphics.Gegenstandsmodell.einreichen(new ItemStack(Items.OAK_BUTTON), mode, stack, consumers.sammler(), BBSRendering.getColorConsumer(color), target.getWorld(), light, overlay);
                    consumers.draw();
                    stack.popPose();
                }

                mchorse.bbs_mod.graphics.Gegenstandsmodell.einreichen(itemStack, mode, stack, consumers.sammler(), BBSRendering.getColorConsumer(color), target.getWorld(), light, overlay);
                consumers.draw();
                consumers.setSubstitute(null);
                FormTranslucentQueue.setSortOrigin(null);


                stack.popPose();

                /* 26.2: nichts mehr zurueckzustellen - der Tiefentest ist kein globaler Zustand mehr,
                 * jeder Draw bekommt ihn aus seiner Pipeline (RenderType#pipeline()). Ersatzlos
                 * entfallen. */
            }
        }
    }

    @Override
    public boolean renderArm(PoseStack matrices, int light, AbstractClientPlayer player, InteractionHand hand)
    {
        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            ArmorSlot slot = hand == InteractionHand.MAIN_HAND ? model.getFpMain() : model.getFpOffhand();

            if (slot == null)
            {
                return false;
            }

            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = Color.white();
            Color formColor = this.form.color.get();

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                ModelGroup g = group;
                boolean visible = false;

                while (g != null)
                {
                    if (g.id.equals(slot.group))
                    {
                        visible = true;

                        break;
                    }

                    g = g.parent;
                }

                group.visible = visible;
            }

            model.model.resetPose();

            matrices.pushPose();
            matrices.mulPose(Axis.YP.rotation(MathUtils.PI));
            MatrixStackUtils.applyTransform(matrices, slot.transform);

            BBSModClient.getTextures().bindTexture(texture);

            Supplier<RenderPipeline> mainShader = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld()) || !model.isVAORendered()
                ? () -> RenderPipelines.ENTITY_TRANSLUCENT_CULL
                : BBSShaders::getModel;

            /* 26.2: enableDepthTest() + enableBlend() sind ersatzlos entfallen. Der Arm laeuft durch
             * dasselbe renderModel wie jedes andere Modell, und Tiefentest wie Mischung kommen aus
             * der Pipeline dieses Draws: ModelVAOPipeline traegt DepthStencilState(
             * GREATER_THAN_OR_EQUAL, true) und BlendFunction.TRANSLUCENT, ohne VAO und unter Iris
             * bringt es die Pipeline des Entity-RenderTypes mit. */

            boolean additive = this.form.additiveColor.get();

            this.renderingArm = true;

            try
            {
                this.renderModel(this.entity, mainShader, matrices, model, light, OverlayTexture.NO_OVERLAY, contextColor, formColor, additive, false, null, 0F, null);
            }
            finally
            {
                this.renderingArm = false;
            }

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                group.visible = true;
            }

            matrices.popPose();

            return true;
        }

        return super.renderArm(matrices, light, player, hand);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = new Color().set(context.color, true);
            Color formColor = this.form.color.get();
            boolean additive = this.form.additiveColor.get();

            if (context.isPicking())
            {
                contextColor.mul(formColor);
                formColor = Color.white();
                additive = false;
            }
            this.evaluateChannels(context.entity, model, context.getTransition());

            context.stack.mulPose(Axis.YP.rotation(MathUtils.PI));
            if (context.world != null)
            {
                context.world.mulPose(Axis.YP.rotation(MathUtils.PI));
            }

            BBSModClient.getTextures().bindTexture(texture);

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);
            boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

            /* Under shaders we can't split opaque/translucent per pixel (Iris strips our PassMode),
             * so a texture with semi-transparent texels would either hide what's behind it or drop
             * the whole model out of Photon's translucent handling. Degrade gracefully to alpha
             * cutout: the cutout program's baked alpha test turns fully-transparent texels into
             * proper holes and draws the rest as a solid, normally-shaded entity. Only for texture
             * translucency at full colour — a uniform colour fade must stay translucent, or the
             * cutout test would erase the whole faded model. */
            boolean cutout = irisWorld && textureObject != null && textureObject.hasTranslucency()
                && contextColor.a >= 1F && formColor.a >= 1F && !additive;

            Supplier<RenderPipeline> mainShader = cutout
                ? () -> RenderPipelines.ENTITY_CUTOUT
                : (irisWorld || !model.isVAORendered())
                    ? () -> RenderPipelines.ENTITY_TRANSLUCENT_CULL
                    : BBSShaders::getModel;
            Supplier<RenderPipeline> shader = this.getShader(context, mainShader, BBSShaders::getPickerModelsProgram);

            boolean wasActive = false;

            if (irisWorld)
            {
                /* Under Iris the model always draws right now, in the phase its program is meant
                 * for. The end-of-frame replay runs after a deferred pack's shading composite —
                 * Photon never shades it and the model vanishes (a 1% colour fade used to fall
                 * into that path). Vanilla translucent entities don't sort either: vanilla-level
                 * blending is the ceiling under shaders, the sorted queue stays a no-shader
                 * feature. */
                wasActive = FormTranslucentQueue.suspend();
            }

            /* 26.2: das disableBlend() des Cutout-Falls ist ersatzlos entfallen. Es wollte den
             * blendfreien Zustand des Vanilla-Cutout-RenderTypes (halbdurchsichtige Texel zeichnen
             * solide, statt ueber den Gbuffer zu schmieren) - und genau der steht jetzt in der
             * Pipeline, nicht in einer Zustandszeile: RenderPipelines.ENTITY_CUTOUT auf dem Weg,
             * den `cutout` oben schon ueber die Programmwahl ansteuert, bzw.
             * ModelVAOPipeline.UNDURCHSICHTIG (ColorTargetState.DEFAULT, keine Mischung) auf dem
             * VAO-Weg. `cutout` bleibt die Bedingung, an der die Pipeline gewaehlt wird. */

            try
            {
                this.renderModel(context.entity, shader, context.stack, model, context.light, context.overlay, contextColor, formColor, additive, false, context.stencilMap, context.getTransition(), context.world);
            }
            finally
            {
                /* 26.2: das enableBlend() des cutout-Falls ist ersatzlos entfallen - es machte nur den
                 * globalen Schalter fuer die folgenden Draws wieder auf, und die holen ihr Blending
                 * aus ihrer Pipeline. Sein Gegenstueck, das disableBlend() oben, ebenso: es waehlt
                 * jetzt die blendfreie Pipeline des Modell-Draws (RenderPipelines.ENTITY_CUTOUT bzw.
                 * ModelVAOPipeline.UNDURCHSICHTIG), statt einen Zustand umzulegen. */

                if (irisWorld)
                {
                    FormTranslucentQueue.restore(wasActive);
                }
            }
        }
    }

    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        ModelInstance model = this.getModel();

        if (model == null || model.model == null || context.stencilMap == null)
        {
            return;
        }

        /* ⛔ DIE BASIS DIESER FORM MERKEN (2026-08-04 gemessen).
         *
         * StencilMap.aktuelleBasis ist EIN Feld auf einer GETEILTEN Karte, und Fuellen und
         * Zeichnen sind zwei getrennte Phasen: updateStencilMap laeuft fuer ALLE Formen,
         * erst danach wird gezeichnet. Der Zeichenaufruf las damit die Basis der ZULETZT
         * gefuellten Form. Am laufenden Spiel mitgeschnitten (Logpoints, Alt gehalten):
         *
         *   FILL basis:=25  karte=65197534     (gewaehltes Replay, Knochen)
         *   FILL basis:=21  karte=65197534
         *   FILL basis:=20  karte=65197534
         *   RENDER kennung=21 karte=65197534   <- dieselbe Karte, fremde Basis
         *
         * Dieselbe Fehlerklasse wie beim Modell-Block (dort per Ringpuffer geloest,
         * 38ecb7079) -- geteilter Zustand zwischen Einreichen und Ausfuehren. */
        this.stencilBasis = context.stencilMap.objectIndex;

        model.fillStencilMap(context.stencilMap, this.form);

        if (this.form != null)
        {
            mchorse.bbs_mod.cubic.ik.ModelIKStencil.renderStencil(context.stack, model.model, this.form.ik.get() instanceof MapType ikMap ? ikMap : null, context.stencilMap, this.form);
        }

        /* Klick-Durchgang der Physik: spiegelt die Attach-Marker in die Auswahl-Maske,
         * damit ein Klick den (oft mesh-losen) Knochen auswaehlt. */
        if (this.form != null && this.form.physics.get() instanceof MapType physicsMap)
        {
            ModelPhysicsDebug.renderStencil(context.stack, model.model, physicsMap, context.stencilMap, this.form);
        }
    }

    private void captureMatrices(ModelInstance model)
    {
        /* this.bones.clear()? */
        model.captureMatrices(this.bones);
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        context.stack.pushPose();
        if (context.world != null)
        {
            context.world.pushPose();
        }

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

            context.stack.pushPose();
            if (context.world != null)
            {
                context.world.pushPose();
            }

            if (matrix != null)
            {
                MatrixStackUtils.multiply(context.stack, matrix);
                if (context.world != null)
                {
                    MatrixStackUtils.multiply(context.world, matrix);
                }
            }
            else
            {
                context.stack.mulPose(Axis.YP.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.mulPose(Axis.YP.rotation(MathUtils.PI));
                }
            }

            this.renderBodyPart(part, context);

            context.stack.popPose();
            if (context.world != null)
            {
                context.world.popPose();
            }
        }

        this.bones.clear();
        context.stack.popPose();
        if (context.world != null)
        {
            context.world.popPose();
        }
    }

    @Override
    public void collectMatrices(IEntity entity, PoseStack stack, MatrixCache matrices, String prefix, float transition)
    {
        ModelInstance model = this.getModel();
        MatrixCacheEntry root = matrices.acquire(prefix);
        Matrix4f mm = root.matrix(), oo = root.origin();

        stack.pushPose();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.last().pose());
        stack.popPose();

        stack.pushPose();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.last().pose());

        /* Collect bones and add them to matrix list */
        if (this.animator != null && model != null)
        {
            this.evaluateChannels(entity, model, transition);

            /* Solve IK here too, so a bone anchored to an IK-driven bone (a head pinned to
             * body_upper) rides the solved pose — these matrices feed the anchor system, the
             * gizmo and trackers, which otherwise see the FK-only pose the render path moved
             * past. The live-drag world-space target overrides need a base transform this
             * local pass doesn't carry, so the config/`ik`-track solve runs (controllers
             * keyed into the pose are already baked in and reached). */
            model.form = this.form;
            stack.mulPose(Axis.YP.rotation(MathUtils.PI));
            this.captureMatrices(model);
        }

        for (Map.Entry<String, MatrixCacheEntry> entry : this.bones.entrySet())
        {
            MatrixCacheEntry captured = matrices.acquire(StringUtils.combinePaths(prefix, entry.getKey()));
            Matrix4f matrix = captured.matrix(), o = captured.origin();

            stack.pushPose();
            MatrixStackUtils.multiply(stack, entry.getValue().matrix());
            matrix.set(stack.last().pose());
            stack.popPose();

            stack.pushPose();
            MatrixStackUtils.multiply(stack, entry.getValue().origin());
            o.set(stack.last().pose());
            stack.popPose();

            captured.setEvaluatedRotation(entry.getValue().evaluatedRotation());
        }

        int i = 0;

        /* Recursively do the same thing with body parts */
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

                stack.pushPose();

                if (matrix != null)
                {
                    MatrixStackUtils.multiply(stack, matrix);
                }
                else
                {
                    stack.mulPose(Axis.YP.rotation(MathUtils.PI));
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(part.getRenderEntity(entity), stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.popPose();
            }

            i += 1;
        }

        stack.popPose();

        this.bones.clear();
    }

    /**
     * Form-local displacement that drags the shadow under the model's perceived position: how far the
     * model has moved from its bind pose, counting BOTH the form's own transform (its keyframes) and
     * the anchor bone's root motion. Falls back to the base form-transform displacement when there's
     * no model or no anchor bone, so every form still shifts its shadow by its transform.
     */
    @Override
    public Vector3f getShadowDisplacement(IEntity entity, float transition)
    {
        ModelInstance model = this.getModel();

        if (model == null)
        {
            return super.getShadowDisplacement(entity, transition);
        }

        String anchor = model.getAnchor();

        if (anchor == null || anchor.isEmpty())
        {
            return super.getShadowDisplacement(entity, transition);
        }

        Vector3f current = this.sampleBoneOrigin(entity, transition, anchor, false);
        Vector3f rest = this.sampleBoneOrigin(entity, transition, anchor, true);

        if (current == null || rest == null)
        {
            return super.getShadowDisplacement(entity, transition);
        }

        return current.sub(rest);
    }

    /**
     * Capture a bone's origin translation in form-local space, either in the current animated pose
     * ({@code rest = false}) or the model's rest/bind pose ({@code rest = true}). Mirrors the root-form
     * portion of {@link #collectMatrices} so both samples share the same frame and the form's own
     * transform cancels out when they are subtracted.
     */
    private Vector3f sampleBoneOrigin(IEntity entity, float transition, String bone, boolean rest)
    {
        ModelInstance model = this.getModel();

        if (model == null)
        {
            return null;
        }

        PoseStack stack = new PoseStack();

        stack.pushPose();

        /* The current sample includes the form's own transform (so its keyframes move the shadow); the
         * rest sample omits it and stays in the bind pose, so subtracting the two yields the full
         * displacement of the model from rest — form transform plus anchor-bone root motion. The
         * model's default scale is static, though, so it must be applied to BOTH samples or it won't
         * cancel and the bind pose ends up at a different height (a constant ~1/16 shadow sink). */
        if (rest)
        {
            Vector3f scale = model.getScale();

            stack.scale(scale.x, scale.y, scale.z);
        }
        else
        {
            this.applyTransforms(stack, false, transition);
        }

        if (rest || this.animator == null)
        {
            model.model.resetPose();
        }
        else
        {
            this.evaluateChannels(entity, model, transition);
        }

        stack.mulPose(Axis.YP.rotation(MathUtils.PI));
        this.captureMatrices(model);

        Vector3f result = null;
        MatrixCacheEntry entry = this.bones.get(bone);

        if (entry != null)
        {
            stack.pushPose();
            MatrixStackUtils.multiply(stack, entry.origin());
            result = stack.last().pose().getTranslation(new Vector3f());
            stack.popPose();
        }

        this.bones.clear();
        stack.popPose();

        return result;
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureAnimator(0F);

        if (this.animator != null)
        {
            this.animator.update(entity);
        }
    }
}
