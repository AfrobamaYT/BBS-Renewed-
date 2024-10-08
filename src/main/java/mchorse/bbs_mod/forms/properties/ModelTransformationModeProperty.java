package mchorse.bbs_mod.forms.properties;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.client.render.model.json.ModelTransformationMode;

public class ModelTransformationModeProperty extends BaseProperty<ModelTransformationMode>
{
    public ModelTransformationModeProperty(Form form, String key, ModelTransformationMode value)
    {
        super(form, key, value);
    }

    @Override
    public BaseType toData()
    {
        return new StringType((this.value == null ? ModelTransformationMode.NONE : this.value).asString());
    }

    @Override
    public void fromData(BaseType data)
    {
        String string = data.isString() ? data.asString() : "";

        this.set(ModelTransformationMode.NONE);

        for (ModelTransformationMode value : ModelTransformationMode.values())
        {
            if (value.asString().equals(string))
            {
                this.set(value);

                break;
            }
        }
    }
}