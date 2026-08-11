package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;
import java.util.function.Consumer;

public interface IUIClipsDelegate extends ICursor
{
    public Film getFilm();

    public Camera getCamera();

    public Clip getClip();

    public default String getClipDisplayName(Clip clip)
    {
        return clip != null ? clip.title.get() : "";
    }

    public void pickClip(Clip clip);

    public void setFlight(boolean flight);

    public boolean isFlying();

    public boolean isRunning();

    public void togglePlayback();

    public boolean canUseKeybinds();

    public void fillData();

    public void embedView(UIElement element);

    /* Undo/redo */

    public void markLastUndoNoMerging();

    public void editMultiple(ValueInt property, int value);

    public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer);

    /**
     * Alle clips, auf die eine aenderung im eigenschaften-panel wirkt — der anker-clip
     * (der, dessen panel offen ist) eingeschlossen, und zwar auch dann, wenn die
     * zeitleisten-auswahl leer ist.
     *
     * <p>ABSICHTLICH ohne default-rumpf: eine leere liste waere ein stiller ausfall des
     * ganzen features. Wer das interface implementiert, muss die frage beantworten.</p>
     */
    public List<Clip> getMehrfachZiele();
}