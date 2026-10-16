package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.UnknownForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.factory.MapFactory;

public class FormArchitect extends MapFactory<Form, Void>
{
    /** Steps addons add to the making of every form, so they can put values of their own on BBS's forms (BBS FS f37a1b632) */
    private static final java.util.List<java.util.function.Consumer<Form>> MODIFIERS = new java.util.ArrayList<>();

    /**
     * The value then saves, reads, gets a track and keyframes like any other; give it the addon's
     * namespace. Palette templates are built directly and stay unmodified; what lands in a scene is
     * a copy, read through {@link #create(Link)}.
     */
    public static void registerModifier(java.util.function.Consumer<Form> modifier)
    {
        MODIFIERS.add(modifier);
    }

    @Override
    public Form create(Link type)
    {
        Form form = super.create(type);

        if (form != null) MODIFIERS.forEach((modifier) -> modifier.accept(form));

        return form;
    }

    @Override
    public String getTypeKey()
    {
        return "id";
    }

    @Override
    public Form createUnknown(Link type, MapType data)
    {
        return new UnknownForm(type);
    }

    public boolean has(MapType data)
    {
        if (data.has(this.getTypeKey()))
        {
            Link id = Link.create(data.getString(this.getTypeKey()));

            return this.factory.containsKey(id);
        }

        return false;
    }
}
