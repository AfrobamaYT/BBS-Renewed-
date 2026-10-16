package mchorse.bbs_mod.cubic.physics;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What an anchored form rides — its anchor's target frame (a car, a horse, a seat bone) — and how much of
 * that frame's motion the form's bone physics feels. The film lays it down each frame for the actor and
 * its body parts; the solver carries the chains along with the share of the carrier's motion they are not
 * meant to feel, so a passenger's hair hangs still in a moving car and still swings to whatever the
 * passenger does inside it. From BBS FS 89b786ca6 (Wemppy4/bbs-fs, MIT).
 *
 * @param position the carrier's origin, in the world space the chains live in
 * @param rotation the carrier's orientation, scale stripped
 * @param feel     share of the carrier's motion the physics feels: 0 rides the carrier fully, 1 is the plain world
 */
public record PhysicsCarrier(Vector3f position, Quaternionf rotation, float feel)
{
    private static final Map<Form, PhysicsCarrier> CARRIERS = new IdentityHashMap<>();
    private static final Quaternionfc IDENTITY = new Quaternionf();

    public static PhysicsCarrier of(Matrix4f frame, float feel)
    {
        return new PhysicsCarrier(frame.getTranslation(new Vector3f()), frame.getNormalizedRotation(new Quaternionf()), feel);
    }

    /**
     * What the anchored form rides: the frame its anchor resolves to before the anchor's own offset, in
     * world space — moving about inside the carrier is the form's own motion, which the physics should
     * feel. Null when the physics feels the whole world: no target, or an anchor at 1 (the default). A
     * target transition blends the two frames and the two shares the way the form itself is blended.
     */
    public static PhysicsCarrier fuer(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, float transition, FormFrameCache frame)
    {
        if (value == null)
        {
            return null;
        }

        boolean same = value.previous == null || Objects.equals(value, value.previous);
        boolean only = value.previous != null && (value.x == 0F || value.x == 1F);
        Anchor anchor = same || value.x == 1F ? value : value.previous;
        float feel = MathUtils.clamp(same || only ? anchor.physics : Lerps.lerp(value.previous.physics, value.physics, value.x), 0F, 1F);

        if (feel >= 1F)
        {
            return null;
        }

        Matrix4f matrix = BaseFilmController.getEntityMatrix(entities, 0D, 0D, 0D, anchor, defaultMatrix, transition, 0, false, frame);

        if (!same && !only)
        {
            Matrix4f lastMatrix = BaseFilmController.getEntityMatrix(entities, 0D, 0D, 0D, value.previous, defaultMatrix, transition, 0, false, frame);

            matrix = lastMatrix == defaultMatrix && matrix == defaultMatrix ? defaultMatrix : Matrices.lerp(lastMatrix, matrix, value.x);
        }

        return matrix == defaultMatrix ? null : of(matrix, feel);
    }

    /** This form and the forms of its body parts ride this carrier in this frame; null rides nothing. */
    public static void setzen(Form form, PhysicsCarrier carrier)
    {
        if (form == null || carrier == null && CARRIERS.isEmpty())
        {
            return;
        }

        if (carrier == null)
        {
            CARRIERS.remove(form);
        }
        else
        {
            CARRIERS.put(form, carrier);
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            setzen(part.getForm(), carrier);
        }
    }

    /** What this form rides for its bone physics in this frame, or null when its physics lives in the world. */
    public static PhysicsCarrier von(Form form)
    {
        return form == null ? null : CARRIERS.get(form);
    }

    /**
     * One tick's ride, made before the sub-steps: where the carrier stood at the last simulated tick
     * against where it stands now. The chain state remembers the new frame at once. Null when there is
     * nothing to carry — no carrier, or the first tick of riding one, or the tick after a seed, which
     * placed the chain afresh: neither has an earlier frame to measure the motion from.
     */
    static Fahrt fahrt(PhysicsCarrier carrier, ChainState state, int age)
    {
        Fahrt fahrt = carrier != null && state.carrierAge == state.lastAge ? new Fahrt(carrier, state) : null;

        state.carrierAge = carrier == null ? Integer.MIN_VALUE : age;

        if (carrier != null)
        {
            state.carrierPosition.set(carrier.position());
            state.carrierRotation.set(carrier.rotation());
        }

        return fahrt;
    }

    static final class Fahrt
    {
        private final PhysicsCarrier carrier;
        private final float share;
        private final Vector3f start, last;
        private final Quaternionf startRotation, lastRotation;
        private final Vector3f step = new Vector3f(), move = new Vector3f();
        private final Quaternionf stepRotation = new Quaternionf(), turn = new Quaternionf();

        private Fahrt(PhysicsCarrier carrier, ChainState state)
        {
            this.carrier = carrier;
            this.share = 1F - carrier.feel();
            this.start = new Vector3f(state.carrierPosition);
            this.last = new Vector3f(state.carrierPosition);
            this.startRotation = new Quaternionf(state.carrierRotation);
            this.lastRotation = new Quaternionf(state.carrierRotation);
        }

        /**
         * The carrier's motion over one sub-step, slid across them like the anchor's: the free points
         * turn about its origin with its share of the turn, then travel with its share of the travel, and
         * their previous positions with them, so their velocity rides along too.
         */
        void schritt(ChainState state, float progress)
        {
            this.step.set(this.start).lerp(this.carrier.position(), progress);
            this.stepRotation.set(this.startRotation).slerp(this.carrier.rotation(), progress);
            this.turn.set(this.lastRotation).conjugate().premul(this.stepRotation);

            if (this.share < 1F)
            {
                IDENTITY.slerp(this.turn, this.share, this.turn);
            }

            this.move.set(this.step).sub(this.last).mul(this.share);

            for (int i = 1; i < state.pos.length; i++)
            {
                this.turn.transform(state.pos[i].sub(this.last)).add(this.last).add(this.move);
                this.turn.transform(state.prev[i].sub(this.last)).add(this.last).add(this.move);
            }

            this.last.set(this.step);
            this.lastRotation.set(this.stepRotation);
        }
    }
}
