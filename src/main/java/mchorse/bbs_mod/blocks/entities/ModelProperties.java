package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.render.model.json.ModelTransformationMode;

public class ModelProperties implements IMapSerializable
{
    private Form form;
    private Form formThirdPerson;
    private Form formInventory;
    private Form formFirstPerson;

    private final Transform transform = new Transform();
    private final Transform transformThirdPerson = new Transform();
    private final Transform transformInventory = new Transform();
    private final Transform transformFirstPerson = new Transform();

    private boolean global;
    private boolean shadow;

    public Form getForm()
    {
        return this.form;
    }

    public void setForm(Form form)
    {
        this.form = form;
    }

    public Form getFormThirdPerson()
    {
        return this.formThirdPerson;
    }

    public void setFormThirdPerson(Form form)
    {
        this.formThirdPerson = form;
    }

    public Form getFormInventory()
    {
        return this.formInventory;
    }

    public void setFormInventory(Form form)
    {
        this.formInventory = form;
    }

    public Form getFormFirstPerson()
    {
        return this.formFirstPerson;
    }

    public void setFormFirstPerson(Form form)
    {
        this.formFirstPerson = form;
    }

    public Transform getTransform()
    {
        return this.transform;
    }

    public Transform getTransformThirdPerson()
    {
        return this.transformThirdPerson;
    }

    public Transform getTransformInventory()
    {
        return this.transformInventory;
    }

    public Transform getTransformFirstPerson()
    {
        return this.transformFirstPerson;
    }

    public boolean isGlobal()
    {
        return this.global;
    }

    public void setGlobal(boolean global)
    {
        this.global = global;
    }

    public boolean getShadow()
    {
        return this.shadow;
    }

    public void setShadow(boolean shadow)
    {
        this.shadow = shadow;
    }

    public Form getForm(ModelTransformationMode mode)
    {
        Form form = this.getForm();

        if (mode == ModelTransformationMode.GUI && this.getFormInventory() != null)
        {
            form = this.getFormInventory();
        }
        else if ((mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND) && this.getFormThirdPerson() != null)
        {
            form = this.getFormThirdPerson();
        }
        else if ((mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) && this.getFormFirstPerson() != null)
        {
            form = this.getFormFirstPerson();
        }

        return form;
    }

    public Transform getTransform(ModelTransformationMode mode)
    {
        Transform transform = this.getTransformThirdPerson();

        if (mode == ModelTransformationMode.GUI)
        {
            transform = this.getTransformInventory();
        }
        else if (mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND)
        {
            transform = this.getTransformFirstPerson();
        }
        else if (mode == ModelTransformationMode.GROUND)
        {
            transform = this.getTransform();
        }

        return transform;
    }

    @Override
    public void fromData(MapType data)
    {
        this.form = FormUtils.fromData(data.getMap("form"));
        this.formThirdPerson = FormUtils.fromData(data.getMap("formThirdPerson"));
        this.formInventory = FormUtils.fromData(data.getMap("formInventory"));
        this.formFirstPerson = FormUtils.fromData(data.getMap("formFirstPerson"));

        this.transform.fromData(data.getMap("transform"));
        this.transformThirdPerson.fromData(data.getMap("transformThirdPerson"));
        this.transformInventory.fromData(data.getMap("transformInventory"));
        this.transformFirstPerson.fromData(data.getMap("transformFirstPerson"));

        this.shadow = data.getBool("shadow");
        this.global = data.getBool("global");
    }

    @Override
    public void toData(MapType data)
    {
        data.put("form", FormUtils.toData(this.form));
        data.put("formThirdPerson", FormUtils.toData(this.formThirdPerson));
        data.put("formInventory", FormUtils.toData(this.formInventory));
        data.put("formFirstPerson", FormUtils.toData(this.formFirstPerson));

        data.put("transform", this.transform.toData());
        data.put("transformThirdPerson", this.transformThirdPerson.toData());
        data.put("transformInventory", this.transformInventory.toData());
        data.put("transformFirstPerson", this.transformFirstPerson.toData());

        data.putBool("shadow", this.shadow);
        data.putBool("global", this.global);
    }

    public void update(IEntity entity)
    {
        if (this.form != null)
        {
            this.form.update(entity);
        }

        if (this.formThirdPerson != null)
        {
            this.formThirdPerson.update(entity);
        }

        if (this.formInventory != null)
        {
            this.formInventory.update(entity);
        }

        if (this.formFirstPerson != null)
        {
            this.formFirstPerson.update(entity);
        }
    }
}
