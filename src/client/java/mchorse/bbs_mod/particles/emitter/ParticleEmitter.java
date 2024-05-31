package mchorse.bbs_mod.particles.emitter;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.IComponentEmitterInitialize;
import mchorse.bbs_mod.particles.components.IComponentEmitterUpdate;
import mchorse.bbs_mod.particles.components.IComponentParticleInitialize;
import mchorse.bbs_mod.particles.components.IComponentParticleRender;
import mchorse.bbs_mod.particles.components.IComponentParticleUpdate;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ParticleEmitter
{
    public ParticleScheme scheme;
    public List<Particle> particles = new ArrayList<>();
    public Map<String, IExpression> variables;

    public Link texture;
    public LivingEntity target;
    public World world;
    public boolean lit;

    public boolean running = true;
    private Particle uiParticle;

    /* Intermediate values */
    public Vector3d lastGlobal = new Vector3d();
    public Matrix3f rotation = new Matrix3f();

    /* Runtime properties */
    public int index;
    public int age;
    public int lifetime;
    public boolean playing = true;
    public boolean paused;

    public float random1 = (float) Math.random();
    public float random2 = (float) Math.random();
    public float random3 = (float) Math.random();
    public float random4 = (float) Math.random();

    /* Camera properties */
    public float cYaw;
    public float cPitch;

    public double cX;
    public double cY;
    public double cZ;

    /* Cached variable references to avoid hash look ups */
    private Variable varIndex;
    private Variable varAge;
    private Variable varLifetime;
    private Variable varRandom1;
    private Variable varRandom2;
    private Variable varRandom3;
    private Variable varRandom4;

    private Variable varEmitterAge;
    private Variable varEmitterLifetime;
    private Variable varEmitterRandom1;
    private Variable varEmitterRandom2;
    private Variable varEmitterRandom3;
    private Variable varEmitterRandom4;

    public double getAge()
    {
        return this.getAge(0);
    }

    public double getAge(float transition)
    {
        return !this.paused ? (this.age + transition) / 20.0 : this.age / 20.0;
    }

    public void setTarget(LivingEntity target)
    {
        this.target = target;
        this.world = target == null ? null : target.getWorld();
    }

    public void setWorld(World world)
    {
        this.world = world;
    }

    public void setScheme(ParticleScheme scheme)
    {
        this.scheme = scheme;

        if (this.scheme == null)
        {
            return;
        }

        this.lit = true;
        this.stop();
        this.start();

        this.setupVariables();
        this.setEmitterVariables(0);

        for (IComponentEmitterInitialize component : this.scheme.emitterInitializes)
        {
            component.apply(this);
        }
    }

    /* Variable related code */

    public void setupVariables()
    {
        this.varIndex = this.scheme.parser.variables.get("variable.particle_index");
        this.varAge = this.scheme.parser.variables.get("variable.particle_age");
        this.varLifetime = this.scheme.parser.variables.get("variable.particle_lifetime");
        this.varRandom1 = this.scheme.parser.variables.get("variable.particle_random_1");
        this.varRandom2 = this.scheme.parser.variables.get("variable.particle_random_2");
        this.varRandom3 = this.scheme.parser.variables.get("variable.particle_random_3");
        this.varRandom4 = this.scheme.parser.variables.get("variable.particle_random_4");

        this.varEmitterAge = this.scheme.parser.variables.get("variable.emitter_age");
        this.varEmitterLifetime = this.scheme.parser.variables.get("variable.emitter_lifetime");
        this.varEmitterRandom1 = this.scheme.parser.variables.get("variable.emitter_random_1");
        this.varEmitterRandom2 = this.scheme.parser.variables.get("variable.emitter_random_2");
        this.varEmitterRandom3 = this.scheme.parser.variables.get("variable.emitter_random_3");
        this.varEmitterRandom4 = this.scheme.parser.variables.get("variable.emitter_random_4");
    }

    public void setParticleVariables(Particle particle, float transition)
    {
        if (this.varIndex != null) this.varIndex.set(particle.index);
        if (this.varAge != null) this.varAge.set(particle.getAge(transition));
        if (this.varLifetime != null) this.varLifetime.set(particle.lifetime / 20.0);
        if (this.varRandom1 != null) this.varRandom1.set(particle.random1);
        if (this.varRandom2 != null) this.varRandom2.set(particle.random2);
        if (this.varRandom3 != null) this.varRandom3.set(particle.random3);
        if (this.varRandom4 != null) this.varRandom4.set(particle.random4);

        this.scheme.updateCurves();
    }

    public void setEmitterVariables(float transition)
    {
        if (this.varEmitterAge != null) this.varEmitterAge.set(this.getAge(transition));
        if (this.varEmitterLifetime != null) this.varEmitterLifetime.set(this.lifetime / 20.0);
        if (this.varEmitterRandom1 != null) this.varEmitterRandom1.set(this.random1);
        if (this.varEmitterRandom2 != null) this.varEmitterRandom2.set(this.random2);
        if (this.varEmitterRandom3 != null) this.varEmitterRandom3.set(this.random3);
        if (this.varEmitterRandom4 != null) this.varEmitterRandom4.set(this.random4);

        this.scheme.updateCurves();
    }

    public void parseVariables(Map<String, String> variables)
    {
        this.variables = new HashMap<>();

        for (Map.Entry<String, String> entry : variables.entrySet())
        {
            this.parseVariable(entry.getKey(), entry.getValue());
        }
    }

    public void parseVariable(String name, String expression)
    {
        try
        {
            this.variables.put(name, this.scheme.parser.parse(expression));
        }
        catch (Exception e)
        {}
    }

    public void replaceVariables()
    {
        if (this.variables == null)
        {
            return;
        }

        for (Map.Entry<String, IExpression> entry : this.variables.entrySet())
        {
            Variable var = this.scheme.parser.variables.get(entry.getKey());

            if (var != null)
            {
                var.set(entry.getValue().get().doubleValue());
            }
        }
    }

    public void start()
    {
        if (this.playing)
        {
            return;
        }

        this.index = 0;
        this.age = 0;
        this.playing = true;
    }

    public void stop()
    {
        if (!this.playing)
        {
            return;
        }

        this.playing = false;

        this.random1 = (float) Math.random();
        this.random2 = (float) Math.random();
        this.random3 = (float) Math.random();
        this.random4 = (float) Math.random();
    }

    /**
     * Update this current emitter
     */
    public void update()
    {
        if (this.scheme == null)
        {
            return;
        }

        this.setEmitterVariables(0);

        for (IComponentEmitterUpdate component : this.scheme.emitterUpdates)
        {
            component.update(this);
        }

        this.setEmitterVariables(0);
        this.updateParticles();

        if (!this.paused)
        {
            this.age += 1;
        }
    }

    /**
     * Update all particles
     */
    private void updateParticles()
    {
        Iterator<Particle> it = this.particles.iterator();

        while (it.hasNext())
        {
            Particle particle = it.next();

            this.updateParticle(particle);

            if (particle.dead)
            {
                it.remove();
            }
        }
    }

    /**
     * Update a single particle
     */
    private void updateParticle(Particle particle)
    {
        particle.update(this);

        this.setParticleVariables(particle, 0);

        for (IComponentParticleUpdate component : this.scheme.particleUpdates)
        {
            component.update(this, particle);
        }
    }

    /**
     * Spawn a particle
     */
    public void spawnParticle()
    {
        if (!this.running)
        {
            return;
        }

        this.particles.add(this.createParticle(false));
    }

    /**
     * Create a new particle
     */
    private Particle createParticle(boolean forceRelative)
    {
        Particle particle = new Particle(this.index);

        this.index += 1;

        this.setParticleVariables(particle, 0);
        particle.setupMatrix(this);

        for (IComponentParticleInitialize component : this.scheme.particleInitializes)
        {
            component.apply(this, particle);
        }

        if (particle.relativePosition && !particle.relativeRotation)
        {
            Vector3f vec = new Vector3f().set(particle.position);

            particle.matrix.transform(vec);
            particle.position.x = vec.x;
            particle.position.y = vec.y;
            particle.position.z = vec.z;
        }

        if (!(particle.relativePosition && particle.relativeRotation))
        {
            particle.position.add(this.lastGlobal);
            particle.initialPosition.add(this.lastGlobal);
        }

        particle.prevPosition.set(particle.position);
        particle.rotation = particle.initialRotation;
        particle.prevRotation = particle.rotation;

        return particle;
    }

    /**
     * Render the particle on screen
     */
    public void renderUI(MatrixStack stack, float transition)
    {
        if (this.scheme == null)
        {
            return;
        }

        List<IComponentParticleRender> list = this.scheme.getComponents(IComponentParticleRender.class);

        if (!list.isEmpty())
        {
            this.bindTexture();

            if (this.uiParticle == null || this.uiParticle.dead)
            {
                this.uiParticle = this.createParticle(true);
            }

            this.rotation.identity();
            this.uiParticle.update(this);
            this.setEmitterVariables(transition);
            this.setParticleVariables(this.uiParticle, transition);

            Matrix4f matrix = stack.peek().getPositionMatrix();
            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

            for (IComponentParticleRender render : list)
            {
                render.renderUI(this.uiParticle, builder, matrix, transition);
            }

            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.disableCull();
            BufferRenderer.drawWithGlobalProgram(builder.end());
            RenderSystem.enableCull();
        }
    }

    /**
     * Render all the particles in this particle emitter
     */
    public void render(Supplier<ShaderProgram> program, MatrixStack stack, float transition)
    {
        if (this.scheme == null)
        {
            return;
        }

        List<IComponentParticleRender> renders = this.scheme.particleRender;

        for (IComponentParticleRender component : renders)
        {
            component.preRender(this, transition);
        }

        if (!this.particles.isEmpty())
        {
            Matrix4f matrix = stack.peek().getPositionMatrix();
            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            this.bindTexture();
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);

            for (Particle particle : this.particles)
            {
                this.setEmitterVariables(transition);
                this.setParticleVariables(particle, transition);

                for (IComponentParticleRender component : renders)
                {
                    component.render(this, particle, builder, matrix, transition);
                }
            }

            RenderSystem.setShader(program);
            RenderSystem.disableCull();
            BufferRenderer.drawWithGlobalProgram(builder.end());
            RenderSystem.enableCull();
        }

        for (IComponentParticleRender component : renders)
        {
            component.postRender(this, transition);
        }
    }

    private void bindTexture()
    {
        Texture texture = BBSModClient.getTextures().getTexture(this.texture == null ? this.scheme.texture : this.texture);

        BBSModClient.getTextures().bindTexture(texture);
    }

    public void setupCameraProperties(Camera camera)
    {
        this.cYaw = 180 - MathUtils.toDeg(camera.rotation.y);
        this.cPitch = MathUtils.toDeg(camera.rotation.x);
        this.cX = camera.position.x;
        this.cY = camera.position.y;
        this.cZ = camera.position.z;
    }
}