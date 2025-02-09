package mchorse.bbs_mod.cubic.model.loaders;

import mchorse.bbs_mod.bobj.BOBJAction;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJChannel;
import mchorse.bbs_mod.bobj.BOBJGroup;
import mchorse.bbs_mod.bobj.BOBJKeyframe;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.animation.AnimationVector;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.interps.Interpolations;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BOBJModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link modelBOBJ = IModelLoader.getLink(model.combine("model.bobj"), links, ".bobj");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");

        try (InputStream stream = models.provider.getAsset(modelBOBJ))
        {
            BOBJLoader.BOBJData bobjData = BOBJLoader.readData(stream);

            if (bobjData.armatures.isEmpty())
            {
                System.err.println("Model \"" + model + "\" doesn't have an armature!");

                return null;
            }

            BOBJArmature armature = bobjData.armatures.values().iterator().next();
            BOBJLoader.BOBJMesh finalMesh = null;

            for (BOBJLoader.BOBJMesh mesh : bobjData.meshes)
            {
                if (mesh.armature == armature)
                {
                    finalMesh = mesh;

                    break;
                }
            }

            if (finalMesh != null)
            {
                BOBJLoader.CompiledData compiledData = BOBJLoader.compileMesh(bobjData, finalMesh);
                BOBJModel bobjModel = new BOBJModel(armature, compiledData, id.startsWith("emoticons") && id.endsWith("_simple"));

                bobjData.initiateArmatures();

                ModelInstance instance = new ModelInstance(id, bobjModel, this.convertAnimations(bobjData, new Animations(models.parser)), modelTexture);

                instance.applyConfig(config);

                return instance;
            }

            System.err.println("Model \"" + model + "\" doesn't have a mesh connected to one of the armatures!");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private Animations convertAnimations(BOBJLoader.BOBJData bobjData, Animations animations)
    {
        for (Map.Entry<String, BOBJAction> entry : bobjData.actions.entrySet())
        {
            Animation animation = new Animation(entry.getKey(), animations.parser);

            this.fillAnimation(animation, entry.getValue());
            animations.add(animation);
        }

        return animations;
    }

    private void fillAnimation(Animation animation, BOBJAction value)
    {
        MolangParser parser = animation.parser;

        for (Map.Entry<String, BOBJGroup> entry : value.groups.entrySet())
        {
            Set<Float> time = new HashSet<>();
            AnimationPart part = new AnimationPart(parser);

            for (BOBJChannel channel : entry.getValue().channels)
            {
                for (BOBJKeyframe keyframe : channel.keyframes)
                {
                    time.add(keyframe.frame);
                }
            }

            List<Float> orderedTime = new ArrayList<>(time);

            orderedTime.sort(Float::compareTo);

            for (Float t : orderedTime)
            {
                BOBJChannel x = null;
                BOBJChannel y = null;
                BOBJChannel z = null;

                BOBJChannel sx = null;
                BOBJChannel sy = null;
                BOBJChannel sz = null;

                BOBJChannel rx = null;
                BOBJChannel ry = null;
                BOBJChannel rz = null;

                for (BOBJChannel channel : entry.getValue().channels)
                {
                    if (channel.path.equals("location"))
                    {
                        if (channel.index == 0) x = channel;
                        else if (channel.index == 1) y = channel;
                        else if (channel.index == 2) z = channel;
                    }
                    else if (channel.path.equals("scale"))
                    {
                        if (channel.index == 0) sx = channel;
                        else if (channel.index == 1) sy = channel;
                        else if (channel.index == 2) sz = channel;
                    }
                    else
                    {
                        if (channel.index == 0) rx = channel;
                        else if (channel.index == 1) ry = channel;
                        else if (channel.index == 2) rz = channel;
                    }
                }

                AnimationVector xyz = new AnimationVector();
                AnimationVector scale = new AnimationVector();
                AnimationVector rotation = new AnimationVector();

                xyz.time = scale.time = rotation.time = t / 20F;
                xyz.interp = scale.interp = rotation.interp = Interpolations.HERMITE;
                xyz.x = new MolangValue(parser, new Constant(x == null ? 0D : x.calculate(t)));
                xyz.y = new MolangValue(parser, new Constant(y == null ? 0D : y.calculate(t)));
                xyz.z = new MolangValue(parser, new Constant(z == null ? 0D : z.calculate(t)));
                scale.x = new MolangValue(parser, new Constant(sx == null ? 1D : sx.calculate(t)));
                scale.y = new MolangValue(parser, new Constant(sy == null ? 1D : sy.calculate(t)));
                scale.z = new MolangValue(parser, new Constant(sz == null ? 1D : sz.calculate(t)));
                rotation.x = new MolangValue(parser, new Constant(rx == null ? 0D : rx.calculate(t)));
                rotation.y = new MolangValue(parser, new Constant(ry == null ? 0D : ry.calculate(t)));
                rotation.z = new MolangValue(parser, new Constant(rz == null ? 0D : rz.calculate(t)));

                part.position.keyframes.add(xyz);
                part.scale.keyframes.add(scale);
                part.rotation.keyframes.add(rotation);
            }

            part.position.sort();
            part.scale.sort();
            part.rotation.sort();

            animation.parts.put(entry.getKey(), part);
        }

        /* Insert head keyframes */
        AnimationPart head = animation.parts.get("head");

        if (head == null)
        {
            head = new AnimationPart(parser);

            animation.parts.put("head", head);

            this.fillHeadVariables(parser, head);
        }
        else if (head.rotation.keyframes.isEmpty())
        {
            this.fillHeadVariables(parser, head);
        }

        animation.setLength(value.getDuration() / 20F);
    }

    private void fillHeadVariables(MolangParser parser, AnimationPart head)
    {
        AnimationVector vector = new AnimationVector();

        vector.x = parseExpression(parser, "query.head_pitch / 180 * " + Math.PI);
        vector.y = parseExpression(parser, "-query.head_yaw / 180 * " + Math.PI);
        vector.z = MolangParser.ZERO;

        head.rotation.keyframes.add(vector);
        head.rotation.sort();
    }

    private static MolangExpression parseExpression(MolangParser parser, String expression)
    {
        try
        {
            return new MolangValue(parser, parser.parse(expression));
        }
        catch (Exception e)
        {}

        return MolangParser.ZERO;
    }
}