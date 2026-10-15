package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Navigation ball in the bottom left corner of the film editor's preview
 * (like in Blender and other DCC apps). Available only in the orbit camera
 * mode: dragging the ball rotates the orbit, clicking an axis ball snaps
 * the camera to look along that world axis (and switches the projection to
 * orthographic, see {@link OrbitFilmCameraController#snapToAxis}).
 *
 * The ball is drawn from {@link UIFilmPanel#lastView}, so it always matches
 * the rendered frame, including the camera smoothing.
 *
 * The axes live in the orbit's anchor space: detached that is the world, but
 * when the orbit is attached to a replay, they follow the replay's body yaw —
 * so the axis balls mean "in front of/behind/beside the record", and both the
 * axes and the attached camera turn together with it.
 */
public class OrbitViewGizmo
{
    public static final int RADIUS = 30;
    public static final int PADDING = 6;

    private static final float BALL_RADIUS = 7F;
    private static final int SEGMENTS = 24;
    private static final int DRAG_THRESHOLD = 3;

    private static final int[][] AXES = {
        {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
        {-1, 0, 0}, {0, -1, 0}, {0, 0, -1}
    };
    private static final String[] LABELS = {"X", "Y", "Z"};
    private static final int[] COLORS = {
        Colors.A100 | Colors.RED,
        Colors.A100 | Colors.GREEN,
        Colors.A100 | Colors.BLUE
    };

    private final UIFilmController controller;

    private boolean pressed;
    private int pressedAxis = -1;
    private boolean dragged;
    private int lastX;
    private int lastY;
    private int pressX;
    private int pressY;

    public OrbitViewGizmo(UIFilmController controller)
    {
        this.controller = controller;
    }

    public boolean isActive()
    {
        return BBSSettings.editorOrbitGizmo.get()
            && this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT
            && !this.controller.panel.isFlying()
            && !this.controller.isControlling();
    }

    public boolean mouseClicked(UIContext context, Area area)
    {
        if (!this.isActive() || context.mouseButton != 0)
        {
            return false;
        }

        float dx = context.mouseX - this.centerX(area);
        float dy = context.mouseY - this.centerY(area);
        float r = this.radius() + 2F;

        if (dx * dx + dy * dy > r * r)
        {
            return false;
        }

        this.pressed = true;
        this.dragged = false;
        this.pressedAxis = this.getHoveredAxis(this.getBalls(area), context.mouseX, context.mouseY);
        this.lastX = this.pressX = context.mouseX;
        this.lastY = this.pressY = context.mouseY;

        return true;
    }

    public boolean mouseReleased(UIContext context)
    {
        if (!this.pressed)
        {
            return false;
        }

        boolean snap = !this.dragged && this.pressedAxis >= 0;
        int axis = this.pressedAxis;

        this.reset();

        if (snap)
        {
            this.controller.orbit.snapToAxis(AXES[axis][0], AXES[axis][1], AXES[axis][2]);
            UIUtils.playClick();
        }

        return true;
    }

    public void render(UIContext context, Area area)
    {
        if (!this.isActive())
        {
            this.reset();

            return;
        }

        this.handleDragging(context);

        float cx = this.centerX(area);
        float cy = this.centerY(area);
        float mdx = context.mouseX - cx;
        float mdy = context.mouseY - cy;
        float hoverRadius = this.radius() + 2F;
        boolean hover = this.pressed || mdx * mdx + mdy * mdy <= hoverRadius * hoverRadius;

        Ball[] balls = this.getBalls(area);
        int hoveredAxis = hover && !this.dragged ? this.getHoveredAxis(balls, context.mouseX, context.mouseY) : -1;
        Batcher2D batcher = context.batcher;

        if (hover)
        {
            this.fillCircle(batcher, cx, cy, hoverRadius, Colors.setA(Colors.WHITE, 0.2F));
        }

        Ball[] sorted = balls.clone();

        Arrays.sort(sorted, Comparator.comparingDouble(Ball::z));

        FontRenderer font = batcher.getFont();

        for (Ball ball : sorted)
        {
            int axis = ball.index() % 3;
            boolean positive = ball.index() < 3;
            boolean hovered = ball.index() == hoveredAxis;

            /* Depth cue: balls on the far hemisphere are smaller and dimmer. */
            float t = (ball.z() + 1F) * 0.5F;
            float r = this.ballRadius() * Lerps.lerp(0.85F, 1.05F, t);
            float ring = Math.max(1.5F, r * 0.22F);
            int color = Colors.lerp(0xFF333333, COLORS[axis], Lerps.lerp(0.45F, 1F, t));

            if (positive)
            {
                this.line(batcher, cx, cy, ball.x(), ball.y(), Math.max(1.5F, r * 0.28F), color);
                this.fillCircle(batcher, ball.x(), ball.y(), r, color);
            }
            else
            {
                this.annulus(batcher, ball.x(), ball.y(), r - ring, r, color);
                this.fillCircle(batcher, ball.x(), ball.y(), r - ring, Colors.setA(color, hovered ? 0.6F : 0.25F));
            }

            if (positive || hovered)
            {
                String label = LABELS[axis];

                /* 0xFFFEFEFE reads as white, but dodges the light theme's
                 * white -> black text remap (the ball sits on the video, not
                 * on a themed UI background). */
                int labelColor = hovered ? 0xFFFEFEFE : 0xFF1D1D1D;

                batcher.text(label, ball.x() - font.getWidth(label) / 2F + 0.5F, ball.y() - 3F, labelColor, false);
            }
        }
    }

    private void reset()
    {
        this.pressed = false;
        this.pressedAxis = -1;
        this.dragged = false;
    }

    private void handleDragging(UIContext context)
    {
        if (!this.pressed)
        {
            return;
        }

        int mx = context.mouseX;
        int my = context.mouseY;

        if (!this.dragged && (Math.abs(mx - this.pressX) > DRAG_THRESHOLD || Math.abs(my - this.pressY) > DRAG_THRESHOLD))
        {
            this.dragged = true;
        }

        if (this.dragged)
        {
            this.controller.orbit.rotate(mx - this.lastX, my - this.lastY);
        }

        this.lastX = mx;
        this.lastY = my;
    }

    private float scale()
    {
        return BBSSettings.editorOrbitGizmoScale.get();
    }

    private float radius()
    {
        return RADIUS * this.scale();
    }

    private float ballRadius()
    {
        return BALL_RADIUS * this.scale();
    }

    /**
     * Rechts OBEN, wie im Entwurf ({@code .gizmo{top:8px;right:200px}}) — links unten stand es
     * mitten im Bild und lag ueber dem Motiv. Der Abstand rechts laesst den Reiterstreifen des
     * N-Panels frei (22 px), damit das Gizmo ihn nicht ueberdeckt.
     */
    private float centerX(Area area)
    {
        return area.ex() - PADDING - this.radius() - 24F;
    }

    private float centerY(Area area)
    {
        /* Unter dem Flaechenkopf (26 px), sonst sitzt es auf den Kopfknoepfen. */
        return area.y + 26F + PADDING + this.radius();
    }

    private Ball[] getBalls(Area area)
    {
        Matrix3f view = new Matrix3f(this.controller.panel.lastView);
        float anchorYaw = this.controller.orbit.getAnchorYaw();
        float cx = this.centerX(area);
        float cy = this.centerY(area);
        float arm = this.radius() - this.ballRadius();
        Ball[] balls = new Ball[AXES.length];
        Vector3f vector = new Vector3f();

        for (int i = 0; i < AXES.length; i++)
        {
            /* Anchor space -> world space -> view space */
            vector.set(AXES[i][0], AXES[i][1], AXES[i][2]).rotateY(anchorYaw);
            view.transform(vector);

            /* View space is y-up, screen space is y-down. */
            balls[i] = new Ball(i, cx + vector.x * arm, cy - vector.y * arm, vector.z);
        }

        return balls;
    }

    private int getHoveredAxis(Ball[] balls, int mouseX, int mouseY)
    {
        int result = -1;
        float bestZ = -Float.MAX_VALUE;
        float r = this.ballRadius() + 1F;

        for (Ball ball : balls)
        {
            float dx = mouseX - ball.x();
            float dy = mouseY - ball.y();

            if (dx * dx + dy * dy <= r * r && ball.z() > bestZ)
            {
                bestZ = ball.z();
                result = ball.index();
            }
        }

        return result;
    }

    /* Drawing primitives (winding matches Batcher2D's quads) */

    /* Drawn through the anti-aliased vector path (VektorZeichner) like Blender's navigation
     * gizmo; the hard triangle fans before left stair-stepped edges on every ball and ring
     * (owner, 2026-10-14: "Minecraft-Render-Look ... pixelig"). */
    private void fillCircle(Batcher2D batcher, float x, float y, float radius, int color)
    {
        /* The vector path adds an anti-aliasing seam outside the shape; take it off so the
         * balls keep their size (measured: lines went 4 -> 8 px wide with it left on). */
        float r = Math.max(0.5F, radius - saum() * 0.5F);

        mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.rundeck(batcher, x - r, y - r, r * 2F, r * 2F, r, color);
    }

    private static float saum()
    {
        return 1F / mchorse.bbs_mod.BBSModClient.getGUIScale();
    }

    private void annulus(Batcher2D batcher, float x, float y, float inner, float outer, int color)
    {
        float mitte = (inner + outer) * 0.5F;
        /* Two segments past the start: an open polyline closed on itself left a notch where
         * its caps met. */
        float[] punkte = new float[(SEGMENTS + 3) * 2];

        for (int i = 0; i <= SEGMENTS + 2; i++)
        {
            double a = i / (double) SEGMENTS * Math.PI * 2D;

            punkte[i * 2] = (float) (x - Math.cos(a) * mitte);
            punkte[i * 2 + 1] = (float) (y + Math.sin(a) * mitte);
        }

        mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.linie(batcher, punkte, outer - inner, color, kasten(x, y, outer + 1F));
    }

    private void line(Batcher2D batcher, float x1, float y1, float x2, float y2, float width, int color)
    {
        if ((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) <= 1E-8F)
        {
            return;
        }

        mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.linie(batcher, new float[] {x1, y1, x2, y2}, width, color,
            kasten(Math.min(x1, x2) - width, Math.min(y1, y2) - width, Math.max(x1, x2) + width, Math.max(y1, y2) + width));
    }

    /**
     * Die Flaeche, in der diese Zeichnung liegt.
     *
     * ⛔ NICHT NULL UEBERGEBEN. Der Bereich dient dem Aussortieren -- ein Element ohne
     * Flaeche wird nicht gezeichnet. Beim ersten Umbau des Gizmos stand hier null: die
     * Methoden liefen (762/381/381 Aufrufe je vier Sekunden), die Einreichung kam durch den
     * Waechter (19964 durch, 0 verworfen), und der Bildschirm blieb trotzdem leer. Der
     * funktionierende Nachbar UIColorPicker uebergibt seit jeher ein echtes Rechteck --
     * derselbe Weg, dieselbe Topologie, nur dieser eine Unterschied.
     */
    private static ScreenRectangle kasten(float mx, float my, float radius)
    {
        return kasten(mx - radius, my - radius, mx + radius, my + radius);
    }

    private static ScreenRectangle kasten(float x1, float y1, float x2, float y2)
    {
        int ax = (int) Math.floor(Math.min(x1, x2));
        int ay = (int) Math.floor(Math.min(y1, y2));

        return new ScreenRectangle(ax, ay,
            Math.max(1, (int) Math.ceil(Math.max(x1, x2)) - ax),
            Math.max(1, (int) Math.ceil(Math.max(y1, y2)) - ay));
    }

    /* ⭐ HIER STAND draw(), EIN SOFORTZUG -- UND DAS WAR DER FEHLER (2026-08-01).
     *
     * Alle drei Zeichnungen (Kreis, Ring, Linie) gingen ueber SofortZeichner.zeichnen mit
     * einer eigenen Pipeline. In 26.2 sammelt die Oberflaeche erst und zeichnet danach; ein
     * sofort geoeffneter Durchgang malt VOR dem GUI und verschwindet darunter. Im
     * Paritaetsvergleich gegen 1.21.1 fehlten dem Gizmo darum saemtliche Achsen und Ringe --
     * sichtbar blieben nur die Buchstaben, die ueber den normalen Textweg laufen.
     *
     * Jetzt reihen alle drei ueber Batcher2D.netz ein. Der Batcher lag die ganze Zeit als
     * Parameter daneben und wurde nicht benutzt.
     *
     * Dasselbe Muster wie LineBuilder (a81b4d2a5), Bodengitter und Kamera-Frustum -- der
     * vierte Fall derselben Sorte im Port. */


    private record Ball(int index, float x, float y, float z)
    {}
}
