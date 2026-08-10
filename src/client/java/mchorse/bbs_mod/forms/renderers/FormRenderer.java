package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.LightCoordsUtil;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.world.InteractionHand;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class FormRenderer <T extends Form>
{
    protected T form;

    public FormRenderer(T form)
    {
        this.form = form;
    }

    public T getForm()
    {
        return this.form;
    }

    public List<String> getBones()
    {
        return Collections.emptyList();
    }

    /**
     * Der eigentliche Zeichenaufruf einer Miniatur, gerufen aus
     * {@code MiniaturRenderer.renderToTexture} — also spaeter im Bild und mit gesetztem
     * {@code Zeichenziel}.
     *
     * <p>Existiert nur, weil {@link #renderInUI} geschuetzt ist und der Bild-im-Bild-Zeichner
     * in einem anderen Paket liegt. Keine Logik, nur die Tuer.</p>
     */
    public final void zeichneMiniatur(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.renderInUI(context, x1, y1, x2, y2);
    }

    public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* ⚠️ VERHALTENSAENDERUNG 26.2: die beiden Lichtrichtungen fuer die Formvorschau in
         * der Oberflaeche sind weg. Hier standen (0, 1, -0.2) und (-0.85, 0.85, 1);
         * RenderSystem.setupLevelDiffuseLighting nahm sie entgegen und legte sie global ab.
         *
         * In 26.2 stehen die Lichtrichtungen in einem Uniform-Block auf der Grafikkarte, und
         * Vanilla gibt sie nur als FESTE Voreinstellungen heraus (Lighting.Entry). Ein eigenes
         * Richtungspaar braeuchte einen eigenen Lighting-Block -- ein Entwurf, keine
         * Uebersetzung. ENTITY_IN_UI ist Vanillas Voreinstellung fuer genau diesen Fall.
         * Folge: die Formvorschauen sind etwas anders ausgeleuchtet als vorher. */
        /* ⛔⛔ DIESER AUFRUF WIRKT HIER NICHT — GEMESSEN 2026-08-10, DREI VOREINSTELLUNGEN.
         *
         * A/B gegen den 1.21.1-Zweig: die Formvorschauen rendern mit gleichmaessig ~40 %
         * der Helligkeit (rosa 197,31,143 gegen 81,13,58; gelb 179,159,47 gegen 72,64,19 --
         * derselbe Faktor ueber alle Kanaele, also Intensitaet, nicht Richtung).
         *
         * Probiert und JE GEMESSEN: ENTITY_IN_UI, LEVEL, ITEMS_3D. Alle drei liefern
         * BYTE-IDENTISCHE Bilder. Die Klassenbytes der laufenden Instanz trugen dabei
         * nachweislich den neuen Wert -- der Aufruf kommt also an und bewirkt nichts.
         *
         * WARUM: Lighting haelt einen GpuBuffer (Instanzfeld `buffer`, paddedSize 256).
         * setupFor() schreibt die Lichtrichtungen dort hinein. Wirksam wird das nur, wenn die
         * PIPELINE des Zeichenaufrufs diesen Uniform-Block bindet. BBS' Formzeichner tut das
         * offenbar nicht -- geschrieben wird in einen Puffer, den niemand liest.
         *
         * ⛔ DREI AUFRUFORTE GEMESSEN, KEINER WIRKT: hier, und in UIFormList BEIDE Aufrufe
         * (vor und nach super.render). Auch die Vermutung, der zweite ueberschreibe den
         * ersten, weil der SubmitNodeCollector die Modelle nur EINREICHT und spaeter zeichnet,
         * ist gemessen und falsch -- ITEMS_FLAT gegen ITEMS_3D an der zweiten Stelle aendert
         * ebenfalls kein Pixel.
         *
         * ⛔ NAECHSTER ANLAUF IST KEIN WEITERER setupFor-AUFRUF. Zu messen ist, welche
         * RenderPipeline der Zeichenaufruf am Ende benutzt und ob sie den Lighting-Block
         * ueberhaupt deklariert. 26.2 zeichnet ueber FormSammler/SubmitNodeCollector; 1.21.1
         * ueber CustomVertexConsumerProvider mit 16 eigens zugewiesenen RenderLayern
         * (FormUtilsClient.getProvider). Dort laufen die Wege auseinander. */
        net.minecraft.client.Minecraft.getInstance().gameRenderer.lighting()
            .setupFor(com.mojang.blaze3d.platform.Lighting.Entry.LEVEL);

        /* Messfuehler: zeichnen die Miniaturen (Form-palette, 64x64-vorschau im
         * Modell-editor) ueberhaupt, oder werden sie nur uebermalt? Genau diese Frage
         * hing bei der grossen Vorschau wochenlang -- von aussen sehen beide Faelle
         * gleich aus. Standardmaessig aus, siehe Messpunkt. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("miniatur." + this.getClass().getSimpleName(),
            (x2 - x1) + "x" + (y2 - y1) + " @" + x1 + "," + y1);

        /* ⭐ BILD IM BILD STATT SOFORTZUG. 26.2 zeichnet die GUI in zwei Phasen: dieser
         * Durchlauf SAMMELT nur, GuiRenderer.render() zeichnet danach. Ein sofort
         * geoeffneter Durchgang landet deshalb UNTER dem gesamten GUI und wird vom
         * Panel-Hintergrund uebermalt -- die Miniaturen zeichneten (Zaehler oben: 366x in
         * vier Sekunden) und waren trotzdem unsichtbar.
         *
         * Gemessen im A/B gegen 1.21.1 (Morph-Panel, gleiche Welt, gleicher Standpunkt):
         * die alte Version zeigt elf Modellvorschauen, der Port keine einzige.
         *
         * Derselbe Weg, den schon das Bodengitter (41ef900aa) und die grosse Vorschau
         * (2b95ec8e5) gegangen sind. Siehe MiniaturZustand. */
        /* ⛔ ABGESCHALTET, WEIL ER DAS SPIEL REISST. Der eingereihte Weg (MiniaturZustand)
         * macht die Miniaturen sichtbar -- und kostete zwei Abstuerze:
         *
         *   crash-2026-08-01_17.24.56  Nutzer klickt im Morph-Panel
         *   crash-2026-08-01_17.28.58  Panel nur geoeffnet, kein Klick noetig
         *   beide: IllegalStateException: Pose stack not empty (LevelRenderer.checkPoseStack)
         *
         * Ausgeschlossen: alle Form-Zeichner sind in pushPose/popPose zaehlbar ausgeglichen,
         * zwischen push und pop steht in keinem ein frueher return, und ein try/catch um den
         * Zeichenaufruf aendert nichts -- es ist also KEIN Ausnahme-Leck, sondern ein echtes
         * unausgeglichenes push. Der Verdacht: renderInUI legt auf context.batcher.pose(),
         * und in der Bild-im-Bild-Phase ist das nicht mehr derselbe Stapel wie im
         * UI-Durchlauf.
         *
         * Eine unsichtbare Miniatur ist ein Schoenheitsfehler. Ein Absturz kostet dem Nutzer
         * seine Szene. Bis der Stapel geklaert ist, gilt der alte Weg.
         *
         * MiniaturZustand und MiniaturRenderer bleiben stehen -- samt Registrierung; sie
         * kosten nichts, solange niemand einreiht, und der naechste Anlauf faengt nicht bei
         * null an. Der vermutlich richtige Entwurf steht in docs/26-2-VIEWPORT-BEFUNDE.md:
         * EIN bildschirmgrosser Sammel-Zustand fuer alle Miniaturen statt einer Textur je
         * Zelle -- dann bleiben die Bildschirmkoordinaten gueltig und es gibt genau einen
         * Stapel-Wechsel statt einem je Vorschau. */

        /* ⭐ EINREIHEN STATT SOFORT ZEICHNEN. 26.2 zeichnet die GUI in zwei Phasen: dieser
         * Durchlauf SAMMELT nur, GuiRenderer.render() zeichnet danach. Ein sofort geoeffneter
         * Durchgang landet deshalb UNTER dem gesamten GUI und wird vom Panel-Hintergrund
         * uebermalt -- die Miniaturen zeichneten (Zaehler oben: 366x in vier Sekunden) und
         * waren trotzdem unsichtbar. Im A/B gegen 1.21.1 zeigte das Morph-Panel dort elf
         * Modellvorschauen und der Port keine einzige.
         *
         * Abgegeben wird gesammelt am Ende des Oberflaechen-Durchlaufs, in EINE
         * bildschirmgrosse Textur -- siehe MiniaturSammelZustand fuer den Grund. */
        mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.einreihen(
            this, context, x1, y1, x2, y2);

        FontRenderer font = context.batcher.getFont();
        String name = this.form.name.get();

        if (!name.isEmpty())
        {
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y1 + 6, Colors.WHITE, Colors.ACTIVE | Colors.A50);
        }

        int keybind = this.form.hotkey.get();

        if (keybind > 0)
        {
            name = KeyCodes.getName(keybind);
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y2 - 6 - font.getHeight(), Colors.WHITE, Colors.A50);
        }
    }

    protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);

    public boolean renderArm(PoseStack matrices, int light, AbstractClientPlayer player, InteractionHand hand)
    {
        return false;
    }

    public final void render(FormRenderingContext context)
    {
        if (!this.form.shaderShadow.get() && BBSRendering.isIrisShadowPass())
        {
            return;
        }

        this.form.applyStates(context.transition);

        int light = context.light;
        boolean visible = this.form.visible.get();

        if (!visible)
        {
            return;
        }

        boolean isPicking = context.stencilMap != null;

        context.stack.pushPose();
        if (context.world != null)
        {
            context.world.pushPose();
        }
        this.applyTransforms(context.stack, false, context.getTransition());
        if (context.world != null)
        {
            this.applyTransforms(context.world, false, context.getTransition());
        }

        float lf = 1F - MathUtils.clamp(this.form.lighting.get(), 0F, 1F);
        int u = context.light & '\uffff';
        int v = context.light >> 16 & '\uffff';

        u = (int) Lerps.lerp(u, net.minecraft.util.LightCoordsUtil.pack(15, 0), lf);
        context.light = u | v << 16;

        this.render3D(context);

        if (isPicking)
        {
            this.updateStencilMap(context);
        }

        this.renderBodyParts(context);

        context.stack.popPose();
        if (context.world != null)
        {
            context.world.popPose();
        }

        context.light = light;

        this.form.unapplyStates();
    }

    protected void applyTransforms(PoseStack stack, boolean origin, float transition)
    {
        Transform transform = this.createTransform();

        if (origin)
        {
            stack.translate(transform.translate.x, transform.translate.y, transform.translate.z);
        }
        else
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }

    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        matrix.mul(this.createTransform().createMatrix());
    }

    /**
     * Die Transform der Form, wie sie beim Zeichnen wirkt: eigene Transform, plus Overlay, plus
     * die zusaetzlichen — in genau dieser Reihenfolge zusammengesetzt.
     *
     * <p>Oeffentlich, weil auch ausserhalb des Zeichnens jemand wissen muss, wie gross eine Form
     * in der Welt steht (die Orbit-Kamera rahmt danach). Die Zusammensetzung selbst nachzubauen
     * waere der Fehler: sie ist drei Zeilen lang, jede einzelne davon falsch nachgebaut liefert
     * eine plausible Zahl, und "3.2 statt 3.0 Bloecke" sieht man einer Kamerafahrt nicht an.</p>
     */
    public Transform createTransform()
    {
        Transform transform = new Transform();

        transform.copy(this.form.transform.get());
        this.applyTransform(transform, this.form.transformOverlay.get());

        for (ValueTransform t : this.form.additionalTransforms)
        {
            this.applyTransform(transform, t.get());
        }

        return transform;
    }

    private void applyTransform(Transform transform, Transform overlay)
    {
        transform.translate.add(overlay.translate);
        transform.scale.add(overlay.scale).sub(1, 1, 1);
        transform.addRotation(overlay);
    }

    /**
     * Form-local displacement of the form's origin from its rest pose this frame, used to drag the
     * entity's shadow under the form's perceived position. The base form simply reports its own
     * transform's translation (so transform keyframes shift the shadow); subclasses can override to
     * add their own motion (e.g. {@link ModelFormRenderer} folds in anchor-bone root motion). The
     * caller maps the result to world axes with the render target.
     */
    public Vector3f getShadowDisplacement(IEntity entity, float transition)
    {
        PoseStack stack = new PoseStack();

        stack.pushPose();
        this.applyTransforms(stack, false, transition);

        Vector3f displacement = stack.last().pose().getTranslation(new Vector3f());

        stack.popPose();

        return displacement;
    }

    protected Supplier<RenderPipeline> getShader(FormRenderingContext context, Supplier<RenderPipeline> normal, Supplier<RenderPipeline> picking)
    {
        if (context.isPicking())
        {
            /* 26.2: die Kennung wird nicht mehr als Uniform am Shader gesetzt. Sie reist
             * als Parameter in den Zeichenaufruf (ModelVAORenderer.render(..., kennung))
             * und landet dort im Modell-Block. Ein gesetztes Uniform galt bis zum
             * naechsten Setzen -- wer es vergass, gab einer fremden Form die eigene
             * Kennung, und der Klick traf das Falsche. */
            return picking;
        }

        return normal;
    }

    protected void updateStencilMap(FormRenderingContext context)
    {
        context.stencilMap.addPicking(this.form);
    }

    protected void render3D(FormRenderingContext context)
    {}

    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            this.renderBodyPart(part, context);
        }
    }

    protected void renderBodyPart(BodyPart part, FormRenderingContext context)
    {
        IEntity oldEntity = context.entity;

        context.entity = part.getRenderEntity(oldEntity);

        if (part.getForm() != null)
        {
            context.stack.pushPose();
            if (context.world != null)
            {
                context.world.pushPose();
            }
            MatrixStackUtils.applyTransform(context.stack, part.transform.get());
            if (context.world != null)
            {
                MatrixStackUtils.applyTransform(context.world, part.transform.get());
            }

            FormUtilsClient.render(part.getForm(), context);

            context.stack.popPose();
            if (context.world != null)
            {
                context.world.popPose();
            }
        }

        context.entity = oldEntity;
    }

    public MatrixCache collectMatrices(IEntity entity, float transition)
    {
        MatrixCache map = new MatrixCache();
        PoseStack stack = new PoseStack();

        this.collectMatrices(entity, stack, map, "", transition);

        return map;
    }

    public void collectMatrices(IEntity entity, PoseStack stack, MatrixCache matrices, String prefix, float transition)
    {
        Matrix4f mm = new Matrix4f();
        Matrix4f oo = new Matrix4f();

        stack.pushPose();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.last().pose());
        stack.popPose();

        stack.pushPose();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.last().pose());

        matrices.put(prefix, mm, oo);

        int i = 0;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                stack.pushPose();
                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.popPose();
            }

            i += 1;
        }

        stack.popPose();
    }
}
