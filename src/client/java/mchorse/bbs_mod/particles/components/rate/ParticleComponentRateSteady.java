package mchorse.bbs_mod.particles.components.rate;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.particles.components.IComponentParticleRender;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

public class ParticleComponentRateSteady extends ParticleComponentRate implements IComponentParticleRender
{
    public static final MolangExpression DEFAULT_PARTICLES = new MolangValue(null, new Constant(50));

    public MolangExpression spawnRate = MolangParser.ONE;

    public ParticleComponentRateSteady()
    {
        this.particles = DEFAULT_PARTICLES;
    }

    @Override
    protected void toData(MapType data)
    {
        if (!MolangExpression.isOne(this.spawnRate)) data.put("spawn_rate", this.spawnRate.toData());
        if (!MolangExpression.isConstant(this.particles, 50)) data.put("max_particles", this.particles.toData());
    }

    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has("spawn_rate")) this.spawnRate = parser.parseData(map.get("spawn_rate"));
        if (map.has("max_particles")) this.particles = parser.parseData(map.get("max_particles"));

        return super.fromData(map, parser);
    }

    @Override
    public void preRender(ParticleEmitter emitter, float transition)
    {}

    @Override
    public void render(ParticleEmitter emitter, Particle particle, BufferBuilder builder, Matrix4f matrix, float transition)
    {}

    @Override
    public void renderUI(Particle particle, BufferBuilder builder, Matrix4f matrix, float transition)
    {}

    @Override
    public void postRender(ParticleEmitter emitter, float transition)
    {
        if (emitter.playing && !emitter.paused)
        {
            double particles = emitter.getAge(transition) * this.spawnRate.get();
            double diff = particles - emitter.index;
            double spawn = Math.round(diff);

            if (spawn > 0)
            {
                emitter.setEmitterVariables(transition);

                for (int i = 0; i < spawn; i++)
                {
                    if (emitter.particles.size() < this.particles.get())
                    {
                        emitter.spawnParticle();
                    }
                }
            }
        }
    }

    @Override
    public int getSortingIndex()
    {
        return 10;
    }
}