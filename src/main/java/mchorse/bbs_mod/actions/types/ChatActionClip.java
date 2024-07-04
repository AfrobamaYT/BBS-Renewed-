package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ValueString;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class ChatActionClip extends ActionClip
{
    public final ValueString message = new ValueString("message", "");

    public ChatActionClip()
    {
        this.add(this.message);
    }

    @Override
    public void apply(FakePlayer player, Film film, Replay replay, int tick)
    {
        for (PlayerEntity entity : player.getWorld().getPlayers())
        {
            entity.sendMessage(Text.literal(StringUtils.processColoredText(this.message.get())));
        }
    }

    @Override
    protected Clip create()
    {
        return new ChatActionClip();
    }
}