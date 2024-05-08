package mchorse.bbs_mod.cubic.parsing;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;

public class ModelParser
{
    public static Model parse(MolangParser parser, MapType data)
    {
        Model model = new Model(parser);

        model.fromData(data);
        model.initialize();

        return model;
    }
}