package mchorse.bbs_mod.selectors;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class SelectorOwner
{
    public IEntity entity;

    private Form form;
    private long check;
    private int nbtCheck;
    private CompoundTag lastNbt;

    private LivingEntity mcEntity;

    public SelectorOwner(LivingEntity mcEntity)
    {
        this.mcEntity = mcEntity;
        this.entity = new MCEntity(mcEntity);
    }

    public Form getForm()
    {
        return form;
    }

    /** Form von aussen erzwingen und die automatische neuberechnung einfrieren.
     *
     *  AUTONOMIE 2026-07-27: BBSTools keeper-hook. Der selector leitet die form sonst
     *  periodisch aus den entity-NBT ab; wer sie per skript/UI setzt, will genau das NICHT
     *  ueberschrieben bekommen. check auf MAX_VALUE = naechste pruefung nie faellig,
     *  clearForm() macht es rueckgaengig. */
    public void setForm(Form form)
    {
        this.form = form;
        this.check = Long.MAX_VALUE;
        this.nbtCheck = Integer.MAX_VALUE;

        if (form != null)
        {
            form.playMain();
        }
    }

    /** Erzwungene form loeschen — die naechste pruefung leitet wieder aus NBT ab. */
    public void clearForm()
    {
        this.form = null;
        this.check = 0L;
        this.nbtCheck = 0;
    }

    public void update()
    {
        Level world = this.entity.getWorld();

        if (!world.isClientSide())
        {
            return;
        }

        this.check();
        this.entity.update();

        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    public void check()
    {
        EntitySelectors selectors = BBSModClient.getSelectors();

        if (this.nbtCheck <= 0)
        {
            this.nbtCheck = 10;

            Set<String> keys = createWhitelist();

            /* ⚠️ 26.2: saveWithoutId nimmt keinen CompoundTag mehr und gibt keinen zurueck —
             * es bespielt ein ValueOutput, den fertigen Compound holt buildResult(). Laeuft
             * hier alle 10 Pruefungen, also im heissen Pfad: buildResult() liefert den intern
             * aufgebauten Compound, es entsteht keine zweite Kopie. ProblemReporter.DISCARDING,
             * weil dieses Abbild nur mit dem letzten verglichen und nie gespeichert wird. */
            CompoundTag compound = this.mcEntity.saveWithoutId(new CompoundTag());
            CompoundTag newCompound = new CompoundTag();

            for (String key : keys)
            {
                Tag element = compound.get(key);

                if (element != null)
                {
                    newCompound.put(key, element);
                }
            }

            if (!Objects.equals(newCompound, this.lastNbt))
            {
                this.check = 0;
            }

            this.lastNbt = newCompound;
        }

        if (this.check < selectors.getLastUpdate())
        {
            this.check = selectors.getLastUpdate();

            EntitySelector selectorFor = selectors.getSelectorFor(this.mcEntity);

            if (selectorFor != null)
            {
                this.form = FormUtils.copy(selectorFor.form);

                if (this.form != null)
                {
                    this.form.playMain();
                }
            }
            else
            {
                this.form = null;
            }
        }

        this.nbtCheck -= 1;
    }

    private Set<String> createWhitelist()
    {
        HashSet<String> strings = new HashSet<>();
        String s = BBSSettings.entitySelectorsPropertyWhitelist.get();
        String[] split = s.split(",");

        for (String string : split)
        {
            strings.add(string.trim());
        }

        return strings;
    }
}