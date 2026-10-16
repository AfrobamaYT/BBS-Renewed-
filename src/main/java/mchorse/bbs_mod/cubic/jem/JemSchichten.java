package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A layer over an entity folded into its model as a material — the wool onto the sheep, the outer skin
 * onto the drowned, the armour onto the horse. A layer is the same skeleton drawn again with other
 * boxes, so its cubes go onto the base's bones of the same ids, under the material; a bone the base
 * lacks comes along under its parent's counterpart and stays still: the layer's program is the base's
 * repeated and is not run. From BBS FS 42ee69b2d (Wemppy4/bbs-fs, MIT).
 */
public final class JemSchichten
{
    private JemSchichten()
    {}

    /** @return the quirks met, for the loader to print against the model's name */
    public static Collection<String> graft(JemModelParser.Result base, JemModelParser.Result layer, String material)
    {
        Set<String> warnings = new LinkedHashSet<>();
        Model model = base.model();

        for (ModelGroup root : layer.model().topGroups)
        {
            graft(model, null, root, material, layer.animation(), warnings);
        }

        model.initialize();

        return warnings;
    }

    private static void graft(Model model, ModelGroup parent, ModelGroup source, String material, CemAnimation program, Set<String> warnings)
    {
        ModelGroup target = model.getGroup(source.id);

        if (target == null)
        {
            target = new ModelGroup(source.id);
            target.initial.copy(source.initial);
            target.current.copy(source.initial);

            (parent == null ? model.topGroups : parent.children).add(target);

            if (program.mentions(source.id))
            {
                warnings.add("bone \"" + source.id + "\" is the layer's alone - its animation is left out");
            }
        }

        for (ModelCube cube : source.cubes)
        {
            cube.material = material;
            target.cubes.add(cube);
        }

        for (ModelGroup child : source.children)
        {
            graft(model, target, child, material, program, warnings);
        }
    }
}
