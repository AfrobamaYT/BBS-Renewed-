package mchorse.bbs_mod.video;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.graphics.texture.TextureUpload;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Decoder und Textur je (Video, Besitzer). Die Textur steht unter video:&lt;pfad&gt;#&lt;n&gt; im
 * TextureManager, damit der Billboard-Renderer sie unveraendert zeichnet. Nur Render-Thread.
 */
public final class VideoTextures
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ungenutzte Decoder samt ffmpeg-Prozess fliegen nach dieser Zeit raus. */
    private static final long IDLE_MS = 5000L;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();
    private static long lastSweep;

    private VideoTextures()
    {}

    /**
     * Meldet beim Zeichnen, welche Form mit welcher Entitaet dieses Video zeigt, und liefert den
     * Link der Textur mit dem zuletzt hochgeladenen Bild (null, solange noch keines da ist).
     * Welches Bild das ist, entscheidet {@link #uploadAll()} am naechsten Bildanfang.
     */
    public static Link frame(VideoForm form, IEntity entity, Object owner)
    {
        String path = form.video.get();

        if (path == null || path.isEmpty())
        {
            return null;
        }

        String key = path + "#" + Integer.toHexString(System.identityHashCode(owner));
        Entry entry = ENTRIES.get(key);

        if (entry == null)
        {
            entry = open(path, key);

            if (entry == null)
            {
                return null;
            }

            ENTRIES.put(key, entry);
        }

        entry.used = System.currentTimeMillis();
        entry.form = form;
        entry.entity = entity;

        if (!entry.uploaded)
        {
            entry.decoder.want(frameAt(entry, VideoClock.ticks(entity, 0F)));
        }

        if (!entry.decoder.error().isEmpty() && REPORTED.add(key))
        {
            LOGGER.error("[video] {} failed: {}", path, entry.decoder.error());
        }

        return entry.uploaded ? entry.link : null;
    }

    /** A video clip's picture: the frame of {@code path} at {@code seconds}, looping or held at the end (BBS FS 3efe13ebc) */
    public static Link frame(String path, double seconds, boolean loop, Object owner)
    {
        if (path == null || path.isEmpty())
        {
            return null;
        }

        String key = path + "#" + Integer.toHexString(System.identityHashCode(owner));
        Entry entry = ENTRIES.get(key);

        if (entry == null && (entry = open(path, key)) != null) ENTRIES.put(key, entry);
        if (entry == null) return null;

        entry.used = System.currentTimeMillis();
        entry.form = null;
        entry.clipSeconds = seconds;
        entry.clipLoop = loop;

        if (!entry.uploaded) entry.decoder.want(clipFrame(entry));
        if (!entry.decoder.error().isEmpty() && REPORTED.add(key)) LOGGER.error("[video] {} failed: {}", path, entry.decoder.error());

        return entry.uploaded ? entry.link : null;
    }

    /** The length of the video at {@code path} in seconds, 0 while it cannot be read */
    public static double seconds(String path)
    {
        for (Entry entry : ENTRIES.values()) if (entry.key.startsWith(path + "#")) return entry.decoder.info.seconds;

        return 0D;
    }

    private static long clipFrame(Entry entry)
    {
        VideoInfo info = entry.decoder.info;
        long frames = Math.max(1L, (long) Math.floor(info.seconds * info.fps));
        long frame = (long) Math.floor(Math.max(0D, entry.clipSeconds) * info.fps + 1e-6);

        return entry.clipLoop ? frame % frames : Math.min(frame, frames - 1);
    }

    private static long frameAt(Entry entry, double filmTicks)
    {
        VideoInfo info = entry.decoder.info;
        double seconds = ((filmTicks - entry.form.start.get()) * entry.form.speed.get() + entry.form.offset.get()) / 20D;
        long frames = Math.max(1L, (long) Math.floor(info.seconds * info.fps));
        long frame = (long) Math.floor(Math.max(0D, seconds) * info.fps + 1e-6);

        return entry.form.loop.get() ? frame % frames : Math.min(frame, frames - 1);
    }

    /**
     * Am Bildanfang (GameRenderer.render HEAD), ausserhalb jedes Render-Passes: neueste Bilder
     * hochladen. Beim Zeichnen geht das nicht -- im HUD-Formvorschau-Pass warf 26.3
     * "PreparedFrame already in use" (Absturz 2026-10-14 15:31).
     */
    public static void uploadAll()
    {
        if (Minecraft.getInstance().level == null)
        {
            /* Welt verlassen: kein Zug fragt mehr nach Bildern, also auch keine Decoder offen lassen. */
            if (!ENTRIES.isEmpty())
            {
                closeAll();
            }

            return;
        }

        sweep();

        boolean export = BBSModClient.getVideoRecorder().isRecording();
        float transition = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (Entry entry : ENTRIES.values())
        {
            VideoDecoder decoder = entry.decoder;
            Texture texture = entry.texture;

            if (entry.form != null)
            {
                /* Zielbild aus der Film-Zeit DIESES Bildes, nicht aus der des vorigen Zugs (das
                 * hinkte gemessen 2-7 Bilder nach). Beim Export auf genau dieses Bild warten. */
                long frame = frameAt(entry, VideoClock.ticks(entry.entity, transition));

                decoder.want(frame);
                decoder.waitFor(frame, export ? 1000L : 8L);
            }
            else if (entry.clipSeconds >= 0)
            {
                long frame = clipFrame(entry);

                decoder.want(frame);
                decoder.waitFor(frame, export ? 1000L : 8L);
            }

            boolean neu = decoder.upload((buffer) ->
            {
                if (!texture.isValid() || texture.width != decoder.width || texture.height != decoder.height)
                {
                    texture.uploadTexture(GL11.GL_TEXTURE_2D, 0, decoder.width, decoder.height, buffer);
                }
                else
                {
                    TextureUpload.write(texture.gpu(), TextureFormat.RGBA_U8, 0, decoder.width, decoder.height, buffer);
                }
            });

            if (neu || entry.uploaded)
            {
                entry.uploaded = true;
                /* Nach einem Textur-Neuladen ist die Map leer: jedes Bild wieder eintragen kostet nichts. */
                BBSModClient.getTextures().textures.put(entry.link, entry.texture);
            }
        }
    }

    public static File resolve(String path)
    {
        File file = new File(path);

        return file.isAbsolute() ? file : new File(BBSMod.getAssetsFolder(), path);
    }

    public static int openDecoders()
    {
        return ENTRIES.size();
    }

    public static void closeAll()
    {
        for (Entry entry : ENTRIES.values())
        {
            entry.close();
        }

        ENTRIES.clear();
    }

    private static Entry open(String path, String key)
    {
        try
        {
            File file = resolve(path);
            VideoInfo info = VideoInfo.probe(file);
            VideoDecoder decoder = new VideoDecoder(file, info, BBSSettings.videoMaxSide.get());

            LOGGER.info("[video] {}: {}x{} {} fps {} s, decoded at {}x{}", path, info.width, info.height, info.fps, info.seconds, decoder.width, decoder.height);

            Entry entry = new Entry(decoder, Link.create("video:" + key));

            entry.key = key;

            return entry;
        }
        catch (Exception e)
        {
            if (REPORTED.add(key))
            {
                LOGGER.error("[video] cannot open {}: {}", path, e.getMessage());
            }

            return null;
        }
    }

    private static void sweep()
    {
        long now = System.currentTimeMillis();

        if (now - lastSweep < 1000L)
        {
            return;
        }

        lastSweep = now;

        for (Iterator<Entry> it = ENTRIES.values().iterator(); it.hasNext(); )
        {
            Entry entry = it.next();

            if (now - entry.used > IDLE_MS)
            {
                entry.close();
                it.remove();
            }
        }
    }

    private static final class Entry
    {
        final VideoDecoder decoder;
        final Link link;
        final Texture texture = new Texture();
        long used;
        boolean uploaded;
        VideoForm form;
        IEntity entity;
        String key = "";
        double clipSeconds = -1;
        boolean clipLoop;

        Entry(VideoDecoder decoder, Link link)
        {
            this.decoder = decoder;
            this.link = link;
            this.texture.setFormat(TextureFormat.RGBA_U8);
        }

        void close()
        {
            BBSModClient.getTextures().textures.remove(this.link);
            this.decoder.close();
            this.texture.delete();
        }
    }
}
