package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.MinecraftClient;

public class Recorder
{
    public int tick;
    public ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");

    public String filmId;
    public int replayId;

    public Recorder(String filmId, int replayId)
    {
        this.filmId = filmId;
        this.replayId = replayId;
        this.tick = -TimeUtils.toTick(BBSSettings.recordingCountdown.get());
    }

    public void update()
    {
        if (this.tick >= 0)
        {
            Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);

            this.keyframes.record(this.tick, morph.entity, null);
        }

        this.tick += 1;
    }
}