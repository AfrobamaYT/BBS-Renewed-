package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.screenplay.UIAudioPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class UISoundOverlayPanel extends UIStringOverlayPanel
{
    public UIAudioPlayer player;

    private static Set<String> getSoundEvents()
    {
        Set<String> locations = new HashSet<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("audio")))
        {
            boolean supportedExtension = link.path.endsWith(".wav") || link.path.endsWith(".ogg");
            boolean notGenerated = !link.path.startsWith("audio/elevenlabs/");

            if (supportedExtension && notGenerated)
            {
                locations.add(link.toString());
            }
        }

        return locations;
    }

    public UISoundOverlayPanel(Consumer<Link> callback)
    {
        super(UIKeys.OVERLAYS_SOUNDS_MAIN, getSoundEvents(), null);

        this.callback((str) ->
        {
            if (callback != null)
            {
                Link link = Link.create(str);

                callback.accept(link);

                try
                {
                    SoundManager sounds = BBSModClient.getSounds();
                    Wave wave = AudioReader.read(BBSMod.getProvider(), link);
                    SoundPlayer player = this.player.getPlayer();

                    if (player != null)
                    {
                        player.stop();
                    }

                    this.player.loadAudio(wave, sounds.readColorCodes(link));
                }
                catch (Exception e)
                {}
            }
        });

        this.player = new UIAudioPlayer();

        this.content.add(this.player);
        this.player.relative(this.content).x(6).w(1F, -12).h(20);
        this.strings.y(20).h(1F, -20);
    }
}