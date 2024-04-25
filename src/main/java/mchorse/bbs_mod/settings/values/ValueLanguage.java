package mchorse.bbs_mod.settings.values;

/**
 * Value language.
 *
 * <p>This value subclass stores language localization ID. IMPORTANT: the
 * language strings don't get reloaded automatically! You need to attach a
 * callback to the value.</p>
 */
public class ValueLanguage extends ValueString
{
    public ValueLanguage(String id)
    {
        super(id, "");
    }
}