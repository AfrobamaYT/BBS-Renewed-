package mchorse.bbs_mod.cubic;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import mchorse.bbs_mod.cubic.IModel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModelInstance implements IModelInstance
{
    /** Identity NormalMat for the welded immediate draw — its normals are already CPU-transformed to world space. */
    private static final Matrix3f WELD_NORMAL_MAT = new Matrix3f();

    public final String id;
    public IModel model;
    public Animations animations;

    /** The model's intrinsic texture from its loader; {@link ModelConfig#texture} overrides it when set. */
    public Link baseTexture;

    /**
     * Per-material default textures, loaded from the model's {@code textures/<material>/}
     * folders (or synthesized as a 1x1 swatch for flat-color materials). Keyed by material
     * name; the empty key is the model's default texture. Used as the static fallback for a
     * material when no animation track overrides it - see {@link #getMaterialTexture}.
     */
    public Map<String, Link> materialTextures = new HashMap<>();

    /** Ordered, distinct list of material names present on the model (for the editor and resolution). */
    public List<String> materials = new ArrayList<>();

    /** The model's {@code config.json} as an editable value tree; the instance reads every setting from here. */
    public final ModelConfig config;

    /** Welds resolved against the model (groups/cubes/corners). Built lazily on first render, kept across frames. */
    private List<WeldBinding> weldBindings;

    /** Whether the VAO bake skipped some groups (shape-keyed meshes) — those render immediate via the hybrid path. */
    private boolean partialVaos;

    /** Per group, the geometry split into one VAO per material name (empty key = default texture). */
    private Map<ModelGroup, Map<String, ModelVAO>> vaos = new HashMap<>();

    public transient Matrix4f lastBaseTransform;
    public transient Form form;

    public ModelInstance(String id, IModel model, Animations animations, Link texture)
    {
        this.id = id;
        this.model = model;
        this.animations = animations;
        this.baseTexture = texture;
        this.config = new ModelConfig(id);
    }

    @Override
    public IModel getModel()
    {
        return this.model;
    }

    @Override
    public Pose getSneakingPose()
    {
        return this.config.getSneakingPose();
    }

    @Override
    public Animations getAnimations()
    {
        return this.animations;
    }

    public Map<ModelGroup, Map<String, ModelVAO>> getVaos()
    {
        return this.vaos;
    }

    /** Welds resolved against this model, built once. Empty when the model declares none or isn't cubic. */
    public List<WeldBinding> getWeldBindings()
    {
        if (this.weldBindings == null)
        {
            this.weldBindings = new ArrayList<>();

            if (this.model instanceof Model model)
            {
                for (ModelWeld weld : this.config.getWelds())
                {
                    WeldBinding binding = WeldBinding.resolve(model, weld);

                    if (binding != null)
                    {
                        this.weldBindings.add(binding);
                    }
                }
            }
        }

        return this.weldBindings;
    }

    /**
     * Re-resolve welds after the config's weld list was edited: drop the cached bindings (rebuilt on the
     * next render) and refresh the config's derived caches so the new welds take effect.
     */
    public void invalidateWelds()
    {
        this.weldBindings = null;
        this.config.rebuild();
    }

    /**
     * Resolve a material's static default texture: the per-material texture loaded
     * from {@code textures/<material>/} if present, otherwise the supplied fallback
     * (the form/model default texture). Animation tracks layer on top of this at
     * render time (handled by the caller), so this only covers the non-animated default.
     */
    public Link getMaterialTexture(String material, Link fallback)
    {
        Link link = this.materialTextures.get(material);

        return link != null ? link : fallback;
    }

    public String getAnchor()
    {
        String anchor = this.model.getAnchor();
        String anchorGroup = this.config.anchor.get();

        if (anchorGroup.isEmpty() && !anchor.isEmpty())
        {
            return anchor;
        }

        return anchorGroup;
    }

    public void applyConfig(MapType data)
    {
        if (data == null)
        {
            return;
        }

        this.config.fromData(data);
    }

    /* Config accessors — the instance reads all of these from {@link #config}. */

    public Link getTexture()
    {
        Link texture = this.config.getTexture();

        return texture != null ? texture : this.baseTexture;
    }

    public Vector3f getScale()
    {
        return this.config.scale.get();
    }

    public float getUiScale()
    {
        return this.config.uiScale.get();
    }

    public boolean isProcedural()
    {
        return this.config.procedural.get();
    }

    public boolean isCulling()
    {
        return this.config.culling.get();
    }

    public String getPoseGroup()
    {
        String group = this.config.poseGroup.get();

        return group.isEmpty() ? this.id : group;
    }

    public View getView()
    {
        return this.config.getView();
    }

    public Set<String> getDisabledBones()
    {
        return this.config.disabledBones.get();
    }

    public Map<String, String> getFlippedParts()
    {
        return this.config.getFlippedParts();
    }

    public Map<ArmorType, ArmorSlot> getArmorSlots()
    {
        return this.config.getArmorSlots();
    }

    public List<ArmorSlot> getItemsMain()
    {
        return this.config.getItemsMain();
    }

    public List<ArmorSlot> getItemsOff()
    {
        return this.config.getItemsOff();
    }

    public ArmorSlot getFpMain()
    {
        return this.config.getFpMain();
    }

    public ArmorSlot getFpOffhand()
    {
        return this.config.getFpOffhand();
    }

    public void setup()
    {
        if (this.model instanceof BOBJModel model)
        {
            Minecraft.getInstance().execute(model::setup);
        }

        /* A welded or shape-keyed model still builds VAOs: only its welded bones and shape-keyed groups render
         * on the immediate (CPU) path, the rest ride their VAOs on the GPU (see {@link #renderHybrid}). */
        if (this.model instanceof Model model)
        {
            boolean bake = !this.config.onCpu.get();

            this.partialVaos = bake && this.hasShapeKeyedGroups(model);

            if (bake)
            {
                Minecraft.getInstance().execute(() ->
                {
                    CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new PoseStack(), model);
                });
            }
        }
    }

    /** Whether some group carries shape-keyed meshes — the VAO builder skips those, so the render is hybrid. */
    private boolean hasShapeKeyedGroups(Model model)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            for (ModelMesh mesh : group.meshes)
            {
                if (!mesh.data.isEmpty())
                {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isVAORendered()
    {
        /* A welded or shape-keyed model builds VAOs too, but renders through the hybrid path — external
         * callers (shader choice, etc.) must still treat it as non-VAO, so report false for those. */
        if (!this.getWeldBindings().isEmpty() || this.partialVaos)
        {
            return false;
        }

        return !this.vaos.isEmpty() || this.model instanceof BOBJModel;
    }

    public void delete()
    {
        for (Map<String, ModelVAO> groupVaos : this.vaos.values())
        {
            for (ModelVAO value : groupVaos.values())
            {
                value.delete();
            }
        }

        this.vaos.clear();
    }

    /* Rendering */

    public void fillStencilMap(StencilMap stencilMap, ModelForm form)
    {
        /* Der Grundindex DIESER Form — der erste Summand der Kennung, siehe
         * StencilMap.aktuelleBasis und picker_models.fsh:43. Er muss VOR dem ersten
         * addPicking gelesen werden; danach ist objectIndex schon weitergezaehlt.
         *
         * ⚠️ ANNAHME, die die Messung bestaetigen muss: die Reihenfolge unten
         * (orderedGroups bzw. armature.orderedBones) entspricht dem Knochenindex, den
         * BOBJModelVAO.updateMesh als lightBone ins Licht-Attribut schreibt
         * (boneIndexData, Knochen mit dem groessten Gewicht). Nur dann ergibt
         * Grund + Knochen eine eindeutige Kennung. Stimmt die Reihenfolge nicht, zeigt
         * die Gegenprobe einen falschen, aber NICHT-NULL Index — daran ist es zu
         * erkennen, und dann braucht es eine echte Zuordnung statt einer Addition. */
        stencilMap.aktuelleBasis = stencilMap.objectIndex;

        if (this.model instanceof Model model)
        {
            for (ModelGroup group : model.getOrderedGroups())
            {
                stencilMap.addPicking(form, this.getPickingBone(group.id));
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            for (BOBJBone orderedBone : model.getArmature().orderedBones)
            {
                stencilMap.addPicking(form, this.getPickingBone(orderedBone.name));
            }
        }
    }

    /**
     * Resolve a bone's name for stencil picking, redirecting it to another bone when the model's
     * config declares an override (e.g. clicking "torso" selecting "low_body" instead). The bone's
     * own geometry still owns the stencil index — only the bone the click resolves to changes.
     */
    private String getPickingBone(String bone)
    {
        return this.config.getPickingOverrides().getOrDefault(bone, bone);
    }

    public void captureMatrices(MatrixCache bones)
    {
        if (this.model instanceof Model model)
        {
            PoseStack stack = new PoseStack();
            CubicMatrixRenderer renderer = new CubicMatrixRenderer(model);

            CubicRenderer.processRenderModel(renderer, null, stack, model);

            for (ModelGroup group : model.getAllGroups())
            {
                Matrix4f matrix = new Matrix4f(renderer.matrices.get(group.index));
                Matrix4f origin = new Matrix4f(renderer.origins.get(group.index));

                matrix.translate(
                    group.initial.translate.x / 16,
                    group.initial.translate.y / 16,
                    group.initial.translate.z / 16
                );
                matrix.rotateY(MathUtils.PI);
                origin.translate(
                    group.initial.translate.x / 16,
                    group.initial.translate.y / 16,
                    group.initial.translate.z / 16
                );
                origin.rotateY(MathUtils.PI);

                bones.put(group.id, matrix, origin, evaluatedChannelRotation(group.current, group.orient, true));
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            model.getArmature().setupMatrices();

            for (BOBJBone orderedBone : model.getArmature().orderedBones)
            {
                Matrix4f matrix = new Matrix4f();
                Matrix4f origin = new Matrix4f();

                matrix.rotateY(MathUtils.PI).mul(orderedBone.mat);
                origin.rotateY(MathUtils.PI).mul(orderedBone.originMat);

                bones.put(orderedBone.name, matrix, origin, evaluatedChannelRotation(orderedBone.transform, orderedBone.orient, false));
            }
        }
    }

    /**
     * The bone's EVALUATED channel rotation (ZYX euler radians) for the gizmo's
     * additive overlay-editing base, or {@code null} when the render doesn't
     * follow the channels additively: quaternion mode composes multiplicatively,
     * and a composed {@code orient} counts only while it still EQUALS the
     * channel rotation — the first composed layer seeds it FROM the channels
     * (identical by construction), but stacked layers multiply and diverge,
     * and then the additive base model doesn't apply.
     */
    private static Vector3f evaluatedChannelRotation(Transform current, Quaternionf orient, boolean degrees)
    {
        if (current.rotationMode == Transform.RotationMode.QUATERNION)
        {
            return null;
        }

        Vector3f radians = degrees
            ? new Vector3f(
                MathUtils.toRad(current.rotate.x),
                MathUtils.toRad(current.rotate.y),
                MathUtils.toRad(current.rotate.z)
            )
            : new Vector3f(current.rotate);

        if (orient != null)
        {
            Quaternionf channels = Matrices.toQuaternionZYXRadians(radians.x, radians.y, radians.z);

            /* |dot| = cos(θ/2) between the two rotations (double cover); anything
             * under ~1.6° apart means a genuinely multiplicative stack. */
            if (Math.abs(channels.dot(orient)) < 0.9999F)
            {
                return null;
            }
        }

        return radians;
    }

    /**
     * First weld pass: capture the rigid world corners of every welded face with no drawing, then build the seams.
     * Runs a dedicated capture-only renderer that only touches welded cubes (and only their welded face's corners),
     * so it's a light matrix walk over the tree rather than a full per-vertex pass.
     */
    private void captureWelds(List<WeldBinding> bindings, PoseStack stack, Model model, int light, int overlay, StencilMap stencilMap, ShapeKeys keys)
    {
        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                layer.resetCapture();
            }
        }

        CubicCubeRenderer capture = new CubicCubeRenderer(light, overlay, stencilMap, keys);

        capture.setWelds(bindings);
        capture.setCaptureOnly(true);
        CubicRenderer.processRenderModel(capture, null, stack, model);

        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                layer.computeSeam();
            }
        }
    }

    /**
     * Hybrid render: bones with baked VAOs ride the GPU; only actively-bending welded bones and groups
     * with no VAO (shape-keyed meshes, or none baked yet) go through the immediate CPU path, where their
     * cubes deform against the seam or morph. A light capture pass fills the seams first — for picking
     * too, so the stencil matches the deformed geometry.
     */
    private void renderHybrid(PoseStack stack, RenderPipeline shader, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver, Model model, List<WeldBinding> bindings)
    {
        Set<ModelGroup> weldedGroups = new HashSet<>();

        for (WeldBinding binding : bindings)
        {
            weldedGroups.add(binding.sourceGroup);
            weldedGroups.add(binding.targetGroup);
        }

        /* Capture the seams for the visible draw AND for picking: the stencil must match the deformed geometry, or
         * hovering a bent welded bone highlights its wrong, un-sealed rest silhouette at the joint. */
        if (!bindings.isEmpty())
        {
            this.captureWelds(bindings, stack, model, light, overlay, stencilMap, keys);
        }

        /* The welded cubes draw immediate with world-space corners, so — outside picking and the Iris pipeline, which
         * run their own shader state — they go through the BBS model shader with NormalMat pinned to identity (the
         * normals are already in world space, the same space the VAO path's NormalMat*Normal resolves to; else the
         * first-person hand during video export inherits a foreign NormalMat and darkens). The VAO bones use the same
         * shader so both halves of the model match. */
        boolean explicitWeld = stencilMap == null && !(BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld());
        RenderPipeline drawShader = explicitWeld ? BBSShaders.getModel() : shader;

        CubicVAORenderer renderProcessor = new CubicVAORenderer(drawShader, this, light, overlay, stencilMap, keys, textureResolver);

        renderProcessor.setColor(color.r, color.g, color.b, color.a);
        renderProcessor.setWelds(bindings);
        renderProcessor.setWeldedGroups(weldedGroups);

        /* 26.2: setShader, getShaderTexture und setShaderTexture sind alle drei entfallen --
         * sie setzten globalen Zustand, den der naechste Zeichenaufruf erbte. Shader steht
         * jetzt in der Pipeline, die Textur in der Bindung des Durchgangs.
         *
         * Was der alte Kommentar hier beschrieb, LOEST SICH DAMIT VON SELBST: der CPU-Weg
         * musste sich die Vorgabetextur merken, weil die VAO-Knochen sie zwischendurch
         * global umbanden. Ein Durchgang kann eine fremde Bindung nicht mehr erben, also
         * gibt es nichts zurueckzustellen -- die Textur reist als Parameter mit. */
        Texture defaultTextureObject = BBSModClient.getTextures().getLastBound();

        /* Open the shared CPU buffer only if some group actually renders on the CPU (a visible bending welded
         * bone, or a visible bone with geometry but no VAO) — drawing an empty buffer would fail. */
        boolean cpuGeometry = this.hasCpuGeometry(model, bindings, weldedGroups);

        /* Messfuehler Aufgabe 96: CPU- oder VAO-Weg? Beide zeichnen SOFORT, also VOR dem GUI.
         *
         * ⚠️ HIER STAND "waeren in der Oberflaeche unsichtbar" -- das war ZU STARK und ist
         * gemessen widerlegt (2026-08-01): im Morphing-Panel laufen dieselben Zaehler
         * (vao-durchgang 3906, sofortzug 62) und das Modell ist deutlich SICHTBAR. Ein
         * Sofortzug ist nicht grundsaetzlich unsichtbar.
         *
         * Er verliert nur gegen alles, was die Oberflaeche DANACH ueber dieselbe Stelle malt.
         * Im Morphing-Panel liegt dort die Welt und niemand malt darueber; im Modell-Editor
         * liegt dort ein undurchsichtiger Panel-Hintergrund. Gemessen: 98,53 % des
         * Vorschaufelds sind exakt #0F1217 = BBSSettings.DARK_DEEP_SURFACE, bei nur 12
         * verschiedenen Farben im ganzen Feld. Standardmaessig aus, siehe Messpunkt. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("modell.cpu-" + cpuGeometry);
        BufferBuilder builder = null;
        ByteBufferBuilder eckenSpeicher = null;

        if (cpuGeometry)
        {
            /* 26.2: Tesselator ist entfallen. Sein geteilter Speicher war ein globaler
             * Zwischenspeicher, den sich alle teilten; jetzt gehoert der Speicher dem
             * Aufruf und wird am Ende wieder freigegeben. */
            eckenSpeicher = new ByteBufferBuilder(4096);
            builder = SofortZeichner.beginnen(eckenSpeicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);
        }

        CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);

        if (cpuGeometry)
        {
            /* Die Normalenmatrix wird nicht mehr vorher am Shader gesetzt -- sie reist
             * als Parameter in drawImmediate und landet dort im Modell-Block. */

            this.drawImmediate(builder, drawShader, stack, explicitWeld ? WELD_NORMAL_MAT : null, stencilMap, defaultTextureObject, color.a);
        }
    }

    /**
     * Draw immediate-path geometry (its vertices carry the full camera-space transform baked in)
     * with two-pass translucency when needed: the opaque texels draw now and write depth, the
     * semi-transparent ones replay from a retained vertex buffer when the frame's translucent
     * queue flushes. Single-pass draws keep the old direct path.
     */
    private void drawImmediate(BufferBuilder builder, RenderPipeline shader, PoseStack stack, Matrix3f normalMat, StencilMap stencilMap, Texture texture, float alpha)
    {
        boolean split = FormTranslucentQueue.needsSplit(shader, stencilMap, texture, alpha);
        boolean whole = !split && FormTranslucentQueue.needsWholeDefer(shader, stencilMap, alpha);

        if (!split && !whole)
        {
            /* Ein Durchgang, kein Aufschieben: Tiefe schreiben, Cull nach Modelleinstellung.
             * build() gibt null zurueck, wenn nichts gezeichnet wurde -- SofortZeichner
             * kommt damit zurecht und zeichnet dann nichts. */
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("modell.sofortzug");

            SofortZeichner.zeichnen(ModelVAOPipeline.waehlenVerschraenkt(this.isCulling(), true), builder.build(),
                new Matrix4f(RenderSystem.getModelViewMatrixCopy()), null,
                texture == null ? null : SofortZeichner.entityTextur(texture), null);

            return;
        }

        /* Both passes (and the deferred flush) need the geometry, so it's retained in our own
         * vertex buffer — drawWithGlobalProgram would consume it. The command owns the buffer
         * and frees it after the flush. */
        /* 26.2: VertexBuffer gibt es nicht mehr. Das Netz bleibt trotzdem liegen -- es wird
         * zweimal gezeichnet (undurchsichtig jetzt, halbdurchsichtig beim Leeren der
         * Warteschlange), und genau dafuer ist GehaltenesNetz da. */
        GehaltenesNetz netz = new GehaltenesNetz(builder.buildOrThrow());

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());

        if (split)
        {
            /* Immediate opaque pass: the solid texels draw now and write depth. (The whole-defer
             * case skips this — it replays the entire mesh with depth at flush instead.) */

            /* Undurchsichtiger Teildurchgang: Tiefe schreiben, Cull nach Modelleinstellung.
             * Beides steckt jetzt in der Pipeline statt im globalen Zustand. */
            netz.zeichnen(ModelVAOPipeline.waehlenVerschraenkt(this.isCulling(), true), modelView, null,
                texture == null ? null : SofortZeichner.entityTextur(texture), null,
                (durchgang) -> durchgang.setUniform(ModelVAOPipeline.MODELL_BLOCK,
                    ModelVAORenderer.blockFuer(normalMat != null ? normalMat : new Matrix3f(),
                        net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        FormTranslucentQueue.PASS_OPAQUE, 0)));
        }

        Vector3f origin = modelView.transformPosition(stack.last().pose().getTranslation(new Vector3f()));

        if (split)
        {
            FormTranslucentQueue.add(new FormTranslucentQueue.NetzBefehl(netz, () -> shader, texture, modelView, normalMat, origin, this.isCulling(), null, null));
        }
        else
        {
            /* Uniform colour fade: defer the whole mesh with depth on so it self-occludes. */
            FormTranslucentQueue.add(new FormTranslucentQueue.NetzBefehl(netz, () -> shader, FormTranslucentQueue.PASS_SINGLE, true, texture, modelView, normalMat, origin, this.isCulling(), null, null));
        }
    }

    /** Whether the immediate path will emit anything: a visible bending welded bone, or a visible bone with geometry but no VAO. */
    private boolean hasCpuGeometry(Model model, List<WeldBinding> bindings, Set<ModelGroup> weldedGroups)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (!group.visible || (group.cubes.isEmpty() && group.meshes.isEmpty()))
            {
                continue;
            }

            Map<String, ModelVAO> groupVaos = this.vaos.get(group);

            if ((weldedGroups.contains(group) && WeldBinding.hasActiveSeam(bindings, group)) || groupVaos == null || groupVaos.isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    public void render(PoseStack stack, Supplier<RenderPipeline> program, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver)
    {
        RenderPipeline shader = program.get();
        mchorse.bbs_mod.graphics.Messpunkt.modellPipeline(this.id, shader); /* bbs.mess: kommt-an-pipeline */

        if (this.model instanceof Model model)
        {
            List<WeldBinding> bindings = this.getWeldBindings();

            /* Welds and partially-baked (shape-keyed) models mix VAO and immediate rendering; a partial
             * model whose VAOs aren't baked yet falls through to the plain CPU path below. */
            if (!bindings.isEmpty() || (this.partialVaos && !this.vaos.isEmpty()))
            {
                this.renderHybrid(stack, shader, color, light, overlay, stencilMap, keys, textureResolver, model, bindings);
            }
            else if (this.isVAORendered())
            {
                CubicVAORenderer renderProcessor = new CubicVAORenderer(shader, this, light, overlay, stencilMap, keys, textureResolver);

                renderProcessor.setColor(color.r, color.g, color.b, color.a);
                CubicRenderer.processRenderModel(renderProcessor, null, stack, model);
            }
            else
            {
                CubicCubeRenderer renderProcessor = new CubicCubeRenderer(light, overlay, stencilMap, keys);

                renderProcessor.setColor(color.r, color.g, color.b, color.a);
                /* 26.2: setShader entfaellt -- der Shader steht in der Pipeline, die
                 * drawImmediate weiter unten waehlt. Der Eckenspeicher gehoert jetzt dem
                 * Aufruf statt einem geteilten Tesselator. */
                ByteBufferBuilder eckenSpeicher = new ByteBufferBuilder(4096);
                BufferBuilder builder = SofortZeichner.beginnen(eckenSpeicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);
                CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);
                this.drawImmediate(builder, shader, stack, null, stencilMap, BBSModClient.getTextures().getLastBound(), color.a);
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            List<BOBJModelVAO> vaos = model.getVaos();

            if (!vaos.isEmpty())
            {
                stack.pushPose();
                stack.mulPose(Axis.YP.rotationDegrees(180F));

                model.getArmature().setupMatrices();

                /* One draw per mesh; bind that mesh's resolved texture (mesh name = material). */
                for (BOBJModelVAO vao : vaos)
                {
                    Texture texture = null; float emission = this.config.getMaterialEmission(vao.data.mesh.name);

                    if (textureResolver != null)
                    {
                        Link link = textureResolver.apply(vao.data.mesh.name);

                        if (link != null)
                        {
                            texture = BBSModClient.getTextures().getTexture(link);
                            BBSModClient.getTextures().bindTexture(texture);
                        }
                    }

                    if (texture == null)
                    {
                        /* No per-mesh override — the draw uses the form's base texture bound earlier. */
                        texture = BBSModClient.getTextures().getLastBound();
                    }

                    /* Messfuehler 2026-08-02: der Grundindex kam als 0 an, obwohl er
                     * durchgereicht wird. Diese Zeile trennt die zwei Moeglichkeiten —
                     * stencilMap ist hier null, ODER fillStencilMap lief nie (aktuelleBasis
                     * steht dann auf dem Feld-Vorgabewert 0). */
                    if (mchorse.bbs_mod.graphics.PickZiel.aktiv() != null)
                    mchorse.bbs_mod.graphics.Messpunkt.zaehle("pick.basis",
                        "stencilMap=" + (stencilMap == null ? "NULL" : "da")
                        + " basis=" + (stencilMap == null ? -1 : stencilMap.aktuelleBasis)
                        + " objectIndex=" + (stencilMap == null ? -1 : stencilMap.objectIndex));

                    vao.updateMesh(stencilMap, keys);

                    if (FormTranslucentQueue.needsSplit(shader, stencilMap, texture, color.a))
                    {
                        Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                        Matrix3f normalMat = new Matrix3f(stack.last().normal());

                        /* BOBJModelVAO zeichnet nicht mehr selbst -- es IST das Netz, gezeichnet
                         * wird es vom ModelVAORenderer. Die Durchgangsart ist dabei Parameter
                         * und keine Klammer mehr um den Zug herum, kann also nicht mehr am
                         * naechsten Zug haengenbleiben. */
                        /* Die Textur GEHT MIT: seit 26.2 liest der Zug sie nicht mehr aus
                         * einer globalen Bindung, sie muss an Sampler0 des Durchgangs. Ohne
                         * das zeichnet das Modell weiss (gemessen, Aufgabe 99). */
                        ModelVAORenderer.render(shader, vao, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, FormTranslucentQueue.PASS_OPAQUE, stencilMap == null ? 0 : stencilMap.aktuelleBasis, texture, emission);

                        FormTranslucentQueue.add(new FormTranslucentQueue.BOBJCommand(vao, vao.snapshotArmature(), vao.getUploadCount(), texture, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, emission, this.isCulling()));
                    }
                    else if (FormTranslucentQueue.needsWholeDefer(shader, stencilMap, color.a))
                    {
                        /* A uniform colour fade defers the whole draw into the sorted end-of-frame
                         * pass with depth kept on, so the faded model still self-occludes. */
                        Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                        Matrix3f normalMat = new Matrix3f(stack.last().normal());

                        FormTranslucentQueue.add(new FormTranslucentQueue.BOBJCommand(vao, () -> shader, FormTranslucentQueue.PASS_SINGLE, true, vao.snapshotArmature(), vao.getUploadCount(), texture, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, emission, this.isCulling()));
                    }
                    else
                    {
                        /* Der Weg des Auswahl-Durchgangs: beide Zweige darueber verlangen
                         * stencilMap == null, hier landet das Picken also immer. Der
                         * Grundindex der Form muss darum HIER mit, nicht nur oben. */
                        ModelVAORenderer.render(shader, vao, stack, color.r, color.g, color.b, color.a, light, overlay, texture, stencilMap == null ? 0 : stencilMap.aktuelleBasis, emission);
                    }
                }

                stack.popPose();
            }
        }
    }
}
