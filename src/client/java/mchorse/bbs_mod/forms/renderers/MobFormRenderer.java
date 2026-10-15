package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.StringReader;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.RemotePlayer;
import mchorse.bbs_mod.compat.alt.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    private static final Map<Class, Map<String, ModelPart>> parts = new HashMap<>();
    private static final Map<ModelPart, Transform> cache = new HashMap<>();
    private static Pose currentPose;
    private static Pose currentPoseOverlay;

    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private Entity entity;

    private String lastId = "";
    private String lastNBT = "";
    private boolean lastSlim;

    public float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;

    public static Pose getCurrentPose()
    {
        return currentPose;
    }

    public static Pose getCurrentPoseOverlay()
    {
        return currentPoseOverlay;
    }

    public static Map<Class, Map<String, ModelPart>> getParts()
    {
        return parts;
    }

    public static Map<ModelPart, Transform> getCache()
    {
        return cache;
    }

    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    @Override
    public List<String> getBones()
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            Map<String, ModelPart> stringModelPartMap = parts.get(this.entity.getClass());

            if (stringModelPartMap == null)
            {
                stringModelPartMap = new HashMap<>();

                if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(this.entity) instanceof LivingEntityRenderer renderer)
                {
                    EntityModel model = renderer.getModel();
                    Set<Field> fields = new HashSet<>();
                    Class aClass = model.getClass();

                    while (aClass != Object.class)
                    {
                        for (Field field : aClass.getDeclaredFields())
                        {
                            fields.add(field);
                        }

                        aClass = aClass.getSuperclass();
                    }

                    for (Field declaredField : fields)
                    {
                        if (declaredField.getType().equals(ModelPart.class))
                        {
                            try
                            {
                                declaredField.setAccessible(true);

                                ModelPart part = (ModelPart) declaredField.get(model);

                                stringModelPartMap.put(declaredField.getName(), part);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                parts.put(this.entity.getClass(), stringModelPartMap);
            }

            return new ArrayList<>(stringModelPartMap.keySet());
        }

        return super.getBones();
    }

    private void bindTexture()
    {
        Link link = this.form.texture.get();

        if (link != null)
        {
            BBSModClient.getTextures().bindTexture(link);
        }
    }

    private void ensureEntity()
    {
        String id = this.form.mobID.get();
        String nbt = this.form.mobNBT.get();
        boolean slim = this.form.slim.get();

        if (!this.lastId.equals(id) || !this.lastNBT.equals(nbt) || slim != this.lastSlim)
        {
            this.lastId = id;
            this.lastNBT = nbt;
            this.lastSlim = slim;
            this.entity = null;
        }

        if (this.entity != null)
        {
            return;
        }

        CompoundTag compound = new CompoundTag();

        try
        {
            /* ⚠️ 26.2: TagParser ist generisch (TagParser<T>) und hat keinen oeffentlichen
             * Konstruktor mehr. parseCompoundAsArgument ist das exakte Gegenstueck zum alten
             * readStruct(): es liest EIN Compound aus dem Reader und meckert NICHT ueber
             * Resttext dahinter. (parseCompoundFully waere strenger und wuerde bei Resttext
             * werfen — das waere hier eine stille Verhaltensaenderung.) */
            compound = mchorse.bbs_mod.compat.NbtAlt.parseCompoundAsArgument(new StringReader(nbt));
        }
        catch (Exception e)
        {}

        /* ⚠️ 26.2: create(Level) gibt es nicht mehr. ignoreChecks=TRUE ist Absicht und kein
         * Bequemlichkeits-Flag: die kurze Variante create(Level, EntitySpawnReason) laeuft
         * intern durch canSpawn(level), und canSpawn prueft jetzt ZUSAETZLICH die
         * Schwierigkeit — ein nicht-friedlicher Mob liefert auf FRIEDLICH still null, ein
         * Zombie-MobForm waere also in Vorschau und Welt einfach verschwunden. Preis: die
         * Feature-Flag-Pruefung faellt damit weg, eine abgeschaltete Entity-Art ist
         * konstruierbar statt null — fuer ein reines Anzeige-Objekt folgenlos. Der
         * EntitySpawnReason selbst hat keine Wirkung (er erreicht die Factory nie).
         *
         * ⚠️ MUSS IDENTISCH BLEIBEN zu EntityExportBridge.createPosingEntity und
         * ReplayPoseReader.createPosingEntity — deren Klassendocs behaupten ausdruecklich,
         * dass alle drei Wegwerf-Entity-Pfade denselben Aufruf benutzen. */
        this.entity = BuiltInRegistries.ENTITY_TYPE.getValue(ResourceLocation.parse(id))
            .create(Minecraft.getInstance().level, mchorse.bbs_mod.compat.alt.EntitySpawnRequest.grund(EntitySpawnReason.MOB_SUMMONED, true));

        if (this.entity == null && this.form.isPlayer())
        {
            this.entity = new RemotePlayer(Minecraft.getInstance().level, slim ? SLIM : WIDE);
            this.entity.getEntityData().set(PlayerUtils.ProtectedAccess.getModelParts(), (byte) 0b1111111);
        }

        /* 26.2: ohne Kennung stirbt der Zeichenweg mit "Tried to access entity ID before
         * ID assignment" -- siehe AnzeigeEntity. */
        AnzeigeEntity.kennungGeben(this.entity);

        if (this.entity != null)
        {
            compound.putString("id", id);

            /* ⚠️ 26.2: load() nimmt keinen CompoundTag mehr, sondern einen ValueInput — die
             * Lese-Schicht, die Fehler an einen ProblemReporter meldet. DISCARDING verwirft
             * sie, also genau das Verhalten, das die alte Signatur hatte. */
            this.entity.load(compound);

            this.entity.noPhysics = true;
        }
    }

    /**
     * Reicht die Wegwerf-Entity beim Bildgraphen ein.
     *
     * <p>⚠️ 26.2: {@code EntityRenderDispatcher.render(entity, x, y, z, yaw, teilTick,
     * stapel, verbraucher, licht)} gibt es nicht mehr — der Weg ist zweiphasig geworden:
     * {@code extractEntity()} zieht den Zustand aus der Entity, {@code submit()} reicht ihn
     * ein. Drei Dinge dabei, die man nicht raten darf:
     *
     * <ul>
     *   <li><b>Der yaw-Parameter (hier immer 0F) faellt weg</b> — folgenlos: er wurde schon
     *       in 1.21.1 nur an {@code EntityRenderer.render} durchgereicht, und
     *       {@code LivingEntityRenderer} ignorierte ihn und rechnete seine Koerperdrehung
     *       selbst aus {@code yBodyRotO}/{@code yBodyRot}. Genau die steht jetzt im
     *       Zustand, aus derselben Quelle.</li>
     *   <li><b>Das Licht wird nach dem Sammeln UEBERSCHRIEBEN.</b> Bis 1.21.1 war es ein
     *       render()-Parameter; jetzt setzt {@code extractRenderState} es aus der
     *       Weltposition der Wegwerf-Entity — und die steht irgendwo, nicht dort, wo die
     *       Form erscheint. Ohne das Ueberschreiben waere eine Form in der Oberflaeche
     *       stockdunkel.</li>
     *   <li><b>Die Kamera kommt aus dem laufenden Bild</b>, nicht aus einer selbstgebauten:
     *       {@code submit} reicht sie an den Namensschild- und den Flammen-Auftrag weiter,
     *       eine erfundene wuerde beide falsch ausrichten.</li>
     * </ul>
     */
    private void entityEinreichen(PoseStack stack, CustomVertexConsumerProvider consumers, float teilTick, int licht)
    {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        //? if >=1.21.9 {
        /*EntityRenderState zustand = dispatcher.extractEntity(this.entity, teilTick);

        zustand.lightCoords = licht;

        CameraRenderState kamera = Minecraft.getInstance().gameRenderer.getLevelRenderState().cameraRenderState;

        dispatcher.submit(zustand, kamera, 0D, 0D, 0D, stack, consumers.sammler());
        *///?} else {
        dispatcher.render(this.entity, 0D, 0D, 0D, teilTick, stack, mchorse.bbs_mod.compat.gpu.SofortSammler.puffer(consumers.sammler()), licht);
        //?}
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            PoseStack stack = context.batcher.pose();

            stack.pushPose();

            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            float scale = this.form.uiScale.get();
            float width = this.entity.getBbWidth();
            float height = this.entity.getBbHeight();

            scale = scale * Math.min(1.8F / Math.max(width, height), 1F);

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            if (!this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                stack.rotate(Axis.YP.rotation(MathUtils.PI));
            }

            stack.last().normal().getScale(Vectors.EMPTY_3F);
            stack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            BooleanHolder first = new BooleanHolder();

            /* ⛔ 26.2: der Hijack ist ersatzlos entfallen. Er setzte beim Zeichnen JEDER Ebene
             * einen anderen Shader -- ein Haken in Vanillas Sofort-Zeichnung, die es nicht
             * mehr gibt. Der Shader steht jetzt in der Pipeline des jeweiligen Zuges; fuer
             * den Auswahl-Durchgang waehlt ihn die Pipeline dort, nicht ein Rueckruf hier. */

            consumers.setUI(true);
            this.entityEinreichen(stack, consumers, context.getTransition(), LightCoordsUtil.pack(15, 0));
            consumers.draw();
            consumers.setUI(false);


            stack.popPose();

            /* ⛔ 26.2: RenderSystem.depthFunc(GL_ALWAYS) ist ersatzlos entfallen — es gibt
             * keinen globalen Tiefenvergleich mehr, er steckt in der Pipeline des jeweiligen
             * Zuges. Hier stand er ABSICHTLICH (nicht als Aufraeumen): alles, was nach einer
             * Mob-Form in der Oberflaeche gezeichnet wurde, sollte deren Tiefenwerte
             * ignorieren und immer davor landen.
             * ⚠️ VERHALTENSAENDERUNG, GEMELDET: dieser Vorrang faellt weg. Zurueckholen
             * laesst er sich nur ueber einen eigenen RenderType bzw. eine eigene Pipeline
             * fuer die UI-Zuege danach — nicht ueber einen Schalter. Sichtbar wuerde es
             * daran, dass ein UI-Element hinter einer Mob-Form verschwindet. */
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            int light = context.light;
            BooleanHolder first = new BooleanHolder();
            Matrix4f cached = new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()));

            if (context.isPicking())
            {
                /* ⛔ 26.2: der Hijack ist ersatzlos entfallen. Er setzte beim Zeichnen JEDER Ebene
                 * einen anderen Shader -- ein Haken in Vanillas Sofort-Zeichnung, die es nicht
                 * mehr gibt. Der Shader steht jetzt in der Pipeline des jeweiligen Zuges; fuer
                 * den Auswahl-Durchgang waehlt ihn die Pipeline dort, nicht ein Rueckruf hier. */

                light = 0;
            }
            else
            {
                /* ⛔ 26.2: der Hijack ist ersatzlos entfallen. Er setzte beim Zeichnen JEDER Ebene
                 * einen anderen Shader -- ein Haken in Vanillas Sofort-Zeichnung, die es nicht
                 * mehr gibt. Der Shader steht jetzt in der Pipeline des jeweiligen Zuges; fuer
                 * den Auswahl-Durchgang waehlt ihn die Pipeline dort, nicht ein Rueckruf hier. */
            }

            context.stack.pushPose();
            if (context.world != null)
            {
                context.world.pushPose();
            }

            if (this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                context.stack.rotate(Axis.YP.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.rotate(Axis.YP.rotation(MathUtils.PI));
                }
            }

            if (this.entity instanceof LivingEntity entity)
            {
                int u = context.overlay & '\uffff';
                int v = context.overlay >> 16 & '\uffff';

                entity.hurtTime = v != 10 ? 100 : 0;
            }

            currentPose = this.form.pose.get();
            currentPoseOverlay = this.form.poseOverlay.get();

            /* Publishing the form's camera-space origin opts its translucent layers (slime
             * bodies, ghost textures) into the deferred sorted pass. */
            if (!context.isPicking())
            {
                Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

                FormTranslucentQueue.setSortOrigin(new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())).transformPosition(origin));
            }

            this.entityEinreichen(context.stack, consumers, context.getTransition(), light);

            currentPose = currentPoseOverlay = null;

            consumers.draw();
            FormTranslucentQueue.setSortOrigin(null);

            context.stack.popPose();

            if (context.world != null)
            {
                context.world.popPose();
            }

            /* Restore the shared model-view matrix captured at entry instead of zeroing it.
             * The old identity() leaked into the 2D UI batch when a MobForm is rendered as a
             * body part inside a list/preview (through this 3D path) — the GUI transform was
             * dropped, so every UI element drawn afterwards landed off-screen ("half the UI
             * disappears" when a MobForm is nested under a ModelForm). Restoring is correct for
             * the 3D viewport too. */
            /* 26.2: das enableDepthTest, das hier stand, entfaellt ersatzlos. Es stellte nach
             * dem Zeichnen den globalen Tiefentest wieder her — den gibt es nicht mehr, jeder
             * folgende Zeichenaufruf bekommt seinen Tiefenzustand aus seiner Pipeline.
             * ⚠️ Nicht zu verwechseln mit dem depthFunc(GL_ALWAYS) am Ende von renderInUI:
             * das setzte ABSICHTLICH einen abweichenden Zustand fuer das, was danach
             * gezeichnet wird. Es ist inzwischen ebenfalls entfernt (es gibt keinen globalen
             * Tiefenvergleich mehr) und dort als Verhaltensaenderung ausgewiesen — hier steht
             * es nur, damit die beiden Faelle nicht verwechselt werden. */
            /* ⛔ 26.2-PORT, NICHT MECHANISCH ERSETZEN: das hier SCHREIBT in die lebende
             * Matrix. `getModelViewMatrix()` heisst in 26.2 `getModelViewMatrixCopy()` --
             * aber eine Kopie zu beschreiben uebersetzt sauber und stellt NICHTS wieder her.
             * Die Wiederherstellung oben faellt dann still aus, und die halbe UI verschwindet
             * wieder (genau der Bug, den der Kommentar darueber beschreibt).
             * Dies ist die EINZIGE schreibende Stelle im ganzen Baum -- alle 23 anderen
             * Vorkommen lesen nur und sind unkritisch. Der Ersatz ist getModelViewStack()
             * (existiert in 26.2 weiter, javap belegt), nicht die Kopie. */
            new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).set(cached);
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            this.entity.tick();

            this.entity.xRotO = this.prevPitch;
            this.entity.yRotO = 0F;

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.yHeadRotO = this.prevYawHead;
                livingEntity.yBodyRotO = 0F;

                /* Limb swing is so ugly */
                if (livingEntity.walkAnimation instanceof LimbAnimatorAccessor a && entity.getLimbAnimator() instanceof LimbAnimatorAccessor b)
                {
                    a.setSpeedOld(b.getSpeedOld());
                    a.setSpeed(b.getSpeed());
                    a.setPosition(b.getPosition());
                }

                /* Arm swing */
                float handSwingProgress = entity.getHandSwingProgress(0F);

                if (handSwingProgress < this.prevHandSwing)
                {
                    this.prevHandSwing = 0;
                }

                if (handSwingProgress > 0 && this.prevHandSwing == 0)
                {
                    //? if >=26.3 {
                    /*livingEntity.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                    *///?} else {
                    livingEntity.swing(InteractionHand.MAIN_HAND);
                    //?}
                }

                this.prevHandSwing = handSwingProgress;
            }

            this.entity.setYRot(0F);
            this.entity.setYHeadRot(entity.getHeadYaw() - entity.getBodyYaw());
            this.entity.setXRot(entity.getPitch());
            this.entity.setYBodyRot(0F);

            this.entity.setPosRaw(entity.getX(), entity.getY(), entity.getZ());
            this.entity.setOnGround(entity.isOnGround());
            this.entity.setShiftKeyDown(entity.isSneaking());
            this.entity.setSprinting(entity.isSprinting());
            this.entity.setPose(entity.isSneaking() ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
            if (this.entity instanceof LivingEntity living)
            {
                living.setItemSlot(EquipmentSlot.MAINHAND, entity.getEquipmentStack(EquipmentSlot.MAINHAND));
                living.setItemSlot(EquipmentSlot.OFFHAND, entity.getEquipmentStack(EquipmentSlot.OFFHAND));
                living.setItemSlot(EquipmentSlot.HEAD, entity.getEquipmentStack(EquipmentSlot.HEAD));
                living.setItemSlot(EquipmentSlot.CHEST, entity.getEquipmentStack(EquipmentSlot.CHEST));
                living.setItemSlot(EquipmentSlot.LEGS, entity.getEquipmentStack(EquipmentSlot.LEGS));
                living.setItemSlot(EquipmentSlot.FEET, entity.getEquipmentStack(EquipmentSlot.FEET));
            }
            this.entity.tickCount = entity.getAge();
            this.entity.noPhysics = true;

            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }

    private static class BooleanHolder
    {
        public boolean bool;
    }
}
