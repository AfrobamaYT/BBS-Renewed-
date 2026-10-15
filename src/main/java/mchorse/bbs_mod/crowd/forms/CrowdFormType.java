package mchorse.bbs_mod.crowd.forms;

import java.util.Locale;

/**
 * Catalog of BBS Form swaps that can be applied to villagers via /mobarmy form.
 * Each entry maps to a JSON resource at /assets/bbs/forms/<resourceName>.json.
 */
public enum CrowdFormType
{
    FREAK("villager_freak"),
    TROLLING("villager_trolling"),
    HAPPY("villager_happy"),
    PILLAGER("pillager");

    public final String resourceName;

    CrowdFormType(String resourceName)
    {
        this.resourceName = resourceName;
    }

    public static CrowdFormType byName(String name)
    {
        if (name == null)
        {
            return null;
        }

        try
        {
            return CrowdFormType.valueOf(name.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
