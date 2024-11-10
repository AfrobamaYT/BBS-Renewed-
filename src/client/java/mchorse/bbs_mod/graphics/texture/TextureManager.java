package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.MultiLink;
import mchorse.bbs_mod.utils.resources.MultiLinkThread;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TextureManager implements IWatchDogListener
{
    public final Map<Link, Texture> textures = new HashMap<>();
    public AssetProvider provider;

    private Texture error;
    private TextureExtruder extruder = new TextureExtruder();

    public TextureManager(AssetProvider provider)
    {
        this.provider = provider;
    }

    public TextureExtruder getExtruder()
    {
        return this.extruder;
    }

    public Texture getError()
    {
        if (this.error == null)
        {
            try
            {
                Pixels pixels = Pixels.fromSize(16, 16);
                Color a = new Color().set(0xff009fe0);
                Color b = new Color().set(0xffe00073);

                for (int x = 0; x < pixels.width; x++)
                {
                    for (int y = 0; y < pixels.height; y++)
                    {
                        Color color = a;

                        if ((x / 4) % 2 == 0 ^ (y / 4) % 2 == 0)
                        {
                            color = b;
                        }

                        pixels.setColor(x, y, color);
                    }
                }

                pixels.rewindBuffer();

                Texture texture = new Texture();
                texture.setFilter(GL11.GL_NEAREST);
                texture.uploadTexture(pixels);
                texture.unbind();

                this.error = texture;
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return this.error;
    }

    public void bindTexture(Link texture)
    {
        this.bindTexture(texture, 0);
    }

    public void bindTexture(Link texture, int unit)
    {
        this.bindTexture(this.getTexture(texture), unit);
    }

    public void bindTexture(Texture texture)
    {
        this.bindTexture(texture, 0);
    }

    public void bindTexture(Texture texture, int unit)
    {
        BBSRendering.trackTexture(texture);

        RenderSystem.setShaderTexture(unit, texture.id);
    }

    public void bind(Link texture)
    {
        this.getTexture(texture).bind();
    }

    public void bind(Link texture, int unit)
    {
        this.getTexture(texture).bind(unit);
    }

    public boolean has(Link link)
    {
        return this.getTexture(link) != this.getError();
    }

    public void delete(Link link)
    {
        Texture texture = this.textures.remove(link);

        if (texture != null)
        {
            texture.delete();
        }
    }

    public Texture createTexture(Link link)
    {
        return this.createTexture(link, GL11.GL_NEAREST);
    }

    public Texture createTexture(Link link, int filter)
    {
        Texture texture = this.textures.get(link);

        if (texture == null || texture == this.getError())
        {
            texture = new Texture();
            texture.setFilter(filter);

            this.textures.put(link, texture);
        }

        return texture;
    }

    public Pixels getPixels(Link link) throws Exception
    {
        Pixels pixels;

        if (link instanceof MultiLink)
        {
            pixels = MultiLinkThread.getStreamForMultiLink((MultiLink) link);
        }
        else
        {
            try (InputStream asset = this.provider.getAsset(link))
            {
                pixels = Pixels.fromPNGStream(asset);
            }
        }

        return pixels;
    }

    public Texture getTexture(Link link)
    {
        return this.getTexture(link, GL11.GL_NEAREST);
    }

    public Texture getTexture(Link link, int filter)
    {
        return this.getTexture(link, filter, false);
    }

    public Texture getTexture(Link link, int filter, boolean silent)
    {
        Texture texture = this.textures.get(link);

        if (texture == null)
        {
            try
            {
                Pixels pixels = this.getPixels(link);

                if (pixels != null)
                {
                    texture = new Texture();
                    texture.setFilter(filter);
                    texture.uploadTexture(pixels);
                    texture.unbind();

                    System.out.println("Texture \"" + link + "\" was loaded!");

                    this.textures.put(link, texture);
                }
                else
                {
                    this.textures.put(link, this.getError());

                    return this.getError();
                }
            }
            catch (Exception e)
            {
                if (!silent)
                {
                    e.printStackTrace();
                }

                texture = this.getError();

                this.textures.put(link, texture);
            }
        }

        return texture;
    }

    public void reload()
    {
        this.reload(false);
    }

    public void reload(boolean delete)
    {
        Iterator<Texture> it = this.textures.values().iterator();

        while (it.hasNext())
        {
            Texture texture = it.next();

            if (texture.isRefreshable() || delete)
            {
                texture.delete();

                it.remove();
            }
        }

        this.textures.clear();
        this.extruder.deleteAll();

        if (this.error != null)
        {
            this.error.delete();

            this.error = null;
        }
    }

    public void delete()
    {
        this.reload(true);
    }

    /**
     * Watch dog listener implementation. This method should reload any texture
     * from "assets" source (which is in game's assets folder).
     */
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null)
        {
            return;
        }

        Texture texture = this.textures.remove(link);

        if (texture != null)
        {
            texture.delete();
        }

        this.extruder.delete(link);
    }
}