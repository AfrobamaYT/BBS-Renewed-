package mchorse.bbs_mod.integrations.vitrail;

import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** One frame's complete form traversals, including each child's actual entity and pose. */
public final class VitrailFormReceipts
{
    public record Model(Object vao, Object pipeline, Matrix4f pose, Object texture,
        int light, int overlay, int pass, float r, float g, float b, float a, float emission)
    {
        public Model { pose = new Matrix4f(pose); }
        boolean matches(Model other)
        {
            return this.vao == other.vao && this.pipeline == other.pipeline && this.pose.equals(other.pose)
                && this.texture == other.texture && this.light == other.light && this.overlay == other.overlay
                && this.pass == other.pass && this.r == other.r && this.g == other.g && this.b == other.b
                && this.a == other.a && this.emission == other.emission;
        }
    }
    public record Key(Object form, Object entity, Matrix4f pose, Matrix4f world,
        int light, int overlay, int color, float transition)
    {
        public Key
        {
            pose = new Matrix4f(pose);
            world = world == null ? null : new Matrix4f(world);
        }

        boolean matches(Key other)
        {
            return this.form == other.form && this.entity == other.entity
                && this.pose.equals(other.pose) && java.util.Objects.equals(this.world, other.world)
                && this.light == other.light && this.overlay == other.overlay
                && this.color == other.color && this.transition == other.transition;
        }
    }

    private static final class Visit
    {
        final Key key;
        final List<Model> vaos = new ArrayList<>();
        final List<String> meshes = new ArrayList<>();
        int vaoCursor;
        int meshCursor;
        Visit(Key key) { this.key = key; }
        void rewind() { this.vaoCursor = 0; this.meshCursor = 0; }
    }

    private static final class Traversal
    {
        final Object form;
        final Object entity;
        final List<Visit> visits = new ArrayList<>();
        int cursor;
        Traversal(Object form, Object entity) { this.form = form; this.entity = entity; }
    }

    private final List<Traversal> completed = new ArrayList<>();
    private final List<Visit> stack = new ArrayList<>();
    private Traversal active;
    private boolean replaying;

    public boolean active() { return this.active != null; }
    public boolean replaying() { return this.active != null && this.replaying; }
    public boolean worldScope() { return !this.stack.isEmpty() && this.stack.getLast() != null; }
    public boolean excludedScope() { return !this.stack.isEmpty() && this.stack.getLast() == null; }
    public boolean has(Object form, Object entity)
    {
        return this.find(form, entity) != null;
    }

    private Traversal find(Object form, Object entity)
    {
        for (Traversal traversal : this.completed)
            if (traversal.form == form && traversal.entity == entity) return traversal;
        return null;
    }

    public void begin(Object form, Object entity, boolean replay)
    {
        if (this.active != null) throw new IllegalStateException("Nested native form traversal");
        Traversal traversal = this.find(form, entity);
        if (replay && traversal == null) throw new IllegalStateException("Native form has no early traversal");
        if (!replay && traversal != null) throw new IllegalStateException("Native form captured twice in one frame");
        this.active = replay ? traversal : new Traversal(form, entity);
        this.replaying = replay;
        this.active.cursor = 0;
        for (Visit visit : this.active.visits) visit.rewind();
    }

    /** Null excludes nested offscreen and picking scopes without disturbing their real drawing. */
    public void enter(Key key)
    {
        if (this.active == null) throw new IllegalStateException("Native form scope has no owner");
        if (key == null) { this.stack.add(null); return; }
        Visit visit;
        if (this.replaying)
        {
            if (this.active.cursor >= this.active.visits.size())
                throw new IllegalStateException("Late native form contains an uncaptured child");
            visit = this.active.visits.get(this.active.cursor++);
            if (!visit.key.matches(key)) throw new IllegalStateException("Late native form/entity/pose changed");
        }
        else
        {
            visit = new Visit(key);
            this.active.visits.add(visit);
        }
        this.stack.add(visit);
    }

    public void leave() { this.leave(true); }

    public void leave(boolean completed)
    {
        if (this.stack.isEmpty()) throw new IllegalStateException("Native form scope underflow");
        Visit visit = this.stack.removeLast();
        if (completed && this.replaying && visit != null
            && (visit.vaoCursor != visit.vaos.size() || visit.meshCursor != visit.meshes.size()))
            throw new IllegalStateException("Late native form omitted captured geometry");
    }

    private Visit current()
    {
        return this.worldScope() ? this.stack.getLast() : null;
    }

    public void model(Model draw)
    {
        Visit visit = this.current();
        if (visit == null || this.replaying) throw new IllegalStateException("Model capture lacks a native world owner");
        visit.vaos.add(draw);
    }

    public boolean suppressModel(Model draw)
    {
        Visit visit = this.current();
        if (!this.replaying || visit == null) return false;
        if (visit.vaoCursor >= visit.vaos.size() || !visit.vaos.get(visit.vaoCursor).matches(draw))
            throw new IllegalStateException("Late native VAO/matrix/pipeline/material has no matching early draw");
        visit.vaoCursor++;
        return true;
    }

    public boolean mesh(String kind)
    {
        Visit visit = this.current();
        if (visit == null) return false;
        if (!this.replaying) { visit.meshes.add(kind); return false; }
        if (visit.meshCursor >= visit.meshes.size() || !visit.meshes.get(visit.meshCursor).equals(kind))
            throw new IllegalStateException("Late native mesh has no matching early form/entity/pose");
        visit.meshCursor++;
        return true;
    }

    public void finish() { this.finish(false); }

    public void finish(boolean allowEmpty)
    {
        if (this.active == null || !this.stack.isEmpty()) throw new IllegalStateException("Unbalanced native form traversal");
        if (this.replaying && this.active.cursor != this.active.visits.size())
            throw new IllegalStateException("Late native traversal omitted a captured child");
        if (!this.replaying)
        {
            if (!allowEmpty && this.active.visits.isEmpty()) throw new IllegalStateException("Native traversal captured no form");
            this.completed.add(this.active);
        }
        this.active = null;
        this.replaying = false;
    }

    public void abort()
    {
        this.stack.clear(); this.active = null; this.replaying = false;
    }

    public void clear()
    {
        this.abort(); this.completed.clear();
    }
}
