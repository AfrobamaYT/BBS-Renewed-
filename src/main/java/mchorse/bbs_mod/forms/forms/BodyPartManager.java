package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.interps.IInterp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BodyPartManager implements IMapSerializable
{
    /**
     * Form owner of this body part manager.
     */
    Form owner;

    private final List<BodyPart> parts = new ArrayList<>();

    public BodyPartManager(Form owner)
    {
        this.owner = owner;
    }

    public Form getOwner()
    {
        return this.owner;
    }

    public List<BodyPart> getAll()
    {
        return Collections.unmodifiableList(this.parts);
    }

    public void addBodyPart(BodyPart part)
    {
        part.setManager(this);

        this.parts.add(part);
    }

    public void removeBodyPart(BodyPart part)
    {
        if (this.parts.remove(part))
        {
            part.setManager(null);
        }
    }

    public void update(IEntity target)
    {
        for (BodyPart part : this.parts)
        {
            part.update(target);
        }
    }

    public void tween(BodyPartManager parts, int duration, IInterp interpolation, int offset, boolean playing)
    {
        if (this.parts.size() > parts.parts.size())
        {
            while (this.parts.size() != parts.parts.size())
            {
                this.parts.remove(this.parts.size() - 1);
            }
        }
        else if (this.parts.size() < parts.parts.size())
        {
            for (int i = this.parts.size(); i < parts.parts.size(); i++)
            {
                this.parts.add(parts.parts.get(i).copy());
            }
        }

        for (int i = 0, c = this.parts.size(); i < c; i++)
        {
            BodyPart thisPart = this.parts.get(i);
            BodyPart otherPart = parts.parts.get(i);

            thisPart.tween(otherPart, duration, interpolation, offset, playing);
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof BodyPartManager)
        {
            return Objects.equals(this.parts, ((BodyPartManager) obj).parts);
        }

        return false;
    }

    @Override
    public void toData(MapType data)
    {
        ListType parts = new ListType();

        for (BodyPart bodypart : this.parts)
        {
            parts.add(bodypart.toData());
        }

        if (!parts.isEmpty())
        {
            data.put("parts", parts);
        }
    }

    @Override
    public void fromData(MapType data)
    {
        ListType parts = data.getList("parts");

        for (BodyPart part : this.parts)
        {
            part.setManager(null);
        }

        this.parts.clear();

        for (BaseType partData : parts)
        {
            if (!partData.isMap())
            {
                continue;
            }

            BodyPart bodypart = new BodyPart();

            bodypart.fromData(partData.asMap());
            this.addBodyPart(bodypart);
        }
    }
}