package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One parse's working state: the model being built, its animation, the bone ids taken so far, the
 * shells and the quirks met. From BBS FS 562c23747, 98383551e (Wemppy4/bbs-fs, MIT).
 */
final class JemParse
{
    final Model model;
    final CemAnimation animation;
    final Set<String> ids = new HashSet<>();

    /** The parts the file draws nothing in — the shells a pack only reads, whose pivots are vanilla's to give */
    final Set<ModelGroup> shells = new HashSet<>();
    final Set<String> warnings = new LinkedHashSet<>();

    JemParse(Model model, CemAnimation animation)
    {
        this.model = model;
        this.animation = animation;
    }

    /** Note a quirk once - a pack repeats the same one on every part */
    void warn(String message)
    {
        this.warnings.add(message);
    }

    JemModelParser.Result result()
    {
        return new JemModelParser.Result(this.model, this.animation, this.warnings);
    }
}
