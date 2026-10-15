package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UIFormSearchList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Quick search: type a form name, press Enter, get a replay carrying that form.
 *
 * <p>Adding an actor otherwise means opening the replay list, hitting add, and picking the
 * form out of the full picker grid — three interactions and a mode switch for something the
 * user already knows the name of. This is one keybind, one query, one Enter, and it is gone.</p>
 *
 * <h2>Warum ein KONTEXTMENUE und kein overlay-panel</h2>
 *
 * <p>Die erste fassung war ein {@code UIOverlayPanel}: mittig, 260 breit, 60% der hoehe, mit
 * titelleiste. Der nutzer wollte das nicht — "eine box die nicht die sicht stoert", und die
 * suchzeile GANZ oben. Beides gibt es hier geschenkt, weil es dieselbe form ist wie das
 * kamera-modus-menue, auf das er gezeigt hat: {@link UIContextMenu} setzt sich an die maus,
 * ist so gross wie sein inhalt, hat keine titelleiste — und schliesst bei einem klick
 * daneben.</p>
 *
 * <p>⚠ ESC KAM NICHT GRATIS MIT, entgegen der annahme beim umbau: {@code UIContextMenu}
 * behandelt ESC zwar, sieht die taste aber nie, weil das fokussierte suchfeld sie vorher
 * aufbraucht. Gemessen, dann geloest — siehe {@link SuchFeld}.</p>
 *
 * <p>Die teile darin sind weiter BBS' eigene: {@link UISearchList} ist das
 * suchzeile-ueber-liste-paar, das der picker und jede overlay-liste benutzt (die suchzeile
 * sitzt darin auf y=0 — deshalb steht sie ohne titelleiste automatisch ganz oben), und
 * {@link UIFormSearchList} zeichnet je zeile links die 3D-vorschau und rechts den namen,
 * ueber {@code FormUtilsClient.renderUI} — dasselbe, was das F4-replay-menue tut.</p>
 */
public class UIQuickReplayMenu extends UIContextMenu
{
    /** So breit wie das kamera-modus-menue, auf das der nutzer gezeigt hat. */
    public static final int BREITE = 170;

    /** Sichtbare treffer. Mehr wuerde die box wieder zum sichtblocker machen. */
    public static final int ZEILEN = 8;

    private final UIFilmPanel panel;

    public final UISearchList<Form> searchList;
    public final UIFormSearchList forms;

    /**
     * Frames bis das suchfeld den fokus bekommt; -1 = schon geschehen.
     *
     * <p>⛔ DAS IST DER FIX FUER "Alt+F schreibt schon ein f ins feld": GLFW schickt zum
     * tastendruck noch ein ZEICHEN-event hinterher, und das kam bisher in einem feld an, das
     * im selben frame den fokus bekommen hatte. Abfangen geht nicht — {@code textInput} ist
     * final und reicht das zeichen ZUERST an die kinder, der elternteil sieht es also nie
     * vor der textbox. Also bekommt das feld den fokus einfach erst, wenn das zeichen durch
     * ist. Zwei frames sind bei 60+ FPS unter 35 ms — keine hand tippt so schnell, und
     * ohne fokus hat das zeichen nirgends hin.</p>
     */
    private int fokusIn = -1;

    /** Box an der maus oeffnen. */
    public static void open(UIFilmPanel panel)
    {
        UIContext context = panel.getContext();

        if (context == null || panel.getData() == null)
        {
            return;
        }

        context.replaceContextMenu(new UIQuickReplayMenu(panel));
    }

    public UIQuickReplayMenu(UIFilmPanel panel)
    {
        super();

        this.panel = panel;

        this.forms = new UIFormSearchList((list) -> this.createReplay(list.isEmpty() ? null : list.get(0)));
        this.forms.addLibraryForms();

        this.searchList = new UISearchList<>(this.forms);

        /* ⛔ DAS SUCHFELD AUSTAUSCHEN, damit ESC die box schliesst statt nur den fokus
         * abzugeben — siehe SuchFeld. UISearchList baut seine textbox selbst im
         * konstruktor; sie wird darum hier abgehaengt und durch unsere ersetzt, mit
         * DEMSELBEN filter-aufruf und DENSELBEN massen (0,0 / volle breite / 20 hoch),
         * damit sich am aussehen nichts aendert. label() kommt danach, sonst haengt der
         * platzhalter an der weggeworfenen box. */
        UISearchList<Form> liste = this.searchList;

        liste.search.removeFromParent();
        liste.search = new SuchFeld((str) -> liste.filter(str, false));
        liste.search.relative(liste).set(0, 0, 0, 20).w(1, 0);
        liste.add(liste.search);

        this.searchList.label(UIKeys.GENERAL_SEARCH).full(this);

        this.add(this.searchList);
    }

    /**
     * Suchfeld, das ESC an die box weiterreicht, statt es aufzubrauchen.
     *
     * <h2>Warum das noetig ist</h2>
     *
     * <p>{@code UITextbox.subKeyPressed} faengt ESC ab, solange das feld den fokus hat, und
     * gibt nur den fokus ab. Das ist in BBS ueberall so, und ueberall sonst ist es richtig:
     * dort ist das textfeld EIN element unter vielen, und man will erst das feld verlassen,
     * bevor man das fenster schliesst.</p>
     *
     * <p>Hier ist es falsch, weil die box NUR aus dem feld und seiner trefferliste besteht:
     * das feld hat den fokus, solange die box offen ist. "Erst das feld verlassen" ist
     * damit ein leerer schritt, und der nutzer sah ein ESC, das nichts tut — er musste mit
     * der maus ans X. GEMESSEN 2026-07-28: erstes ESC gab nur den fokus ab, erst das zweite
     * schloss.</p>
     *
     * <p>⚠ BEWUSST NICHT {@code UITextbox} SELBST GEAENDERT: dieselbe zeile haengt an jedem
     * panel mit textfeld im ganzen baum, und das zweistufige verhalten ist dort gewollt.
     * Der eingriff bleibt darum auf diese box beschraenkt.</p>
     */
    private class SuchFeld extends UITextbox
    {
        public SuchFeld(java.util.function.Consumer<String> callback)
        {
            super(100, callback);
        }

        @Override
        public boolean subKeyPressed(UIContext context)
        {
            if (this.isFocused() && context.isPressed(InputConstants.KEY_ESCAPE))
            {
                /* Fokus zuerst abgeben: das feld verschwindet gleich, und ein fokus auf
                 * einem element ohne parent laesst die tastatur ins leere laufen. */
                context.unfocus();

                UIQuickReplayMenu.this.removeFromParent();

                return true;
            }

            return super.subKeyPressed(context);
        }
    }

    /**
     * Leer heisst hier: die bibliothek hat gar keine form. Dann waere die box eine leere
     * huelle, und {@link UIContext#setContextMenu} laesst sie erst gar nicht aufgehen.
     */
    @Override
    public boolean isEmpty()
    {
        return this.forms.getList().isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        /* Suchzeile (20) plus die sichtbaren zeilen. maxH/bounds klemmen die box an den
         * bildschirm, damit sie am unteren rand nicht heraushaengt — dieselbe zeile wie in
         * UISimpleContextMenu. */
        int hoehe = 20 + ZEILEN * UIFormSearchList.ROW_HEIGHT;

        this.set(context.mouseX(), context.mouseY(), BREITE, hoehe)
            .maxH(context.menu.height - 10)
            .bounds(context.menu.overlay, 5);

        this.fokusIn = 2;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.fokusIn > 0)
        {
            this.fokusIn--;
        }
        else if (this.fokusIn == 0)
        {
            this.fokusIn = -1;

            context.focus(this.searchList.search);
        }

        super.render(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* Kein isFocused()-guard: das suchfeld hat den fokus, solange die box offen ist, und
         * diese tasten muessen trotzdem wirken. Enter erreicht uns, weil UITextbox es nur im
         * delayedInput-modus schluckt. ESC faellt ans UIContextMenu durch (super) und
         * schliesst die box. */
        if (context.isPressed(InputConstants.KEY_RETURN) || context.isPressed(InputConstants.KEY_NUMPADENTER))
        {
            this.createReplay(this.forms.getHighlighted());

            return true;
        }
        else if (context.isPressed(InputConstants.KEY_DOWN))
        {
            this.forms.moveHighlight(1);

            return true;
        }
        else if (context.isPressed(InputConstants.KEY_UP))
        {
            this.forms.moveHighlight(-1);

            return true;
        }

        return super.subKeyPressed(context);
    }

    /**
     * Create a NEW replay carrying a copy of the given form and select it.
     *
     * <p>Copied, not referenced: the list holds the library's own form objects, and a replay
     * that shared one would edit the library entry from then on. {@code UIReplayList.openFormEditor}
     * copies for the same reason.</p>
     */
    private void createReplay(Form form)
    {
        Film film = this.panel.getData();

        if (form == null || film == null)
        {
            return;
        }

        Camera camera = this.panel.getCamera();
        Vector3d position = this.getSpawnPosition(camera);

        /* film.replays.addReplay() — BBS' factory (fresh id, default shadow keyframes, pre/post
         * notify so undo and the value tree see it). NOT new Replay(), which skips all of that. */
        Replay replay = film.replays.addReplay();

        replay.category.set("");
        replay.form.set(FormUtils.copy(form));

        /* Same channel set, same values as UIReplayList.addReplay(position, pitch, yaw), so a quick
         * replay lands exactly where the regular "add replay" would. */
        float pitch = camera.rotation.x;
        float yaw = camera.rotation.y + MathUtils.PI;

        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        replay.keyframes.pitch.insert(0, (double) pitch);
        replay.keyframes.yaw.insert(0, (double) yaw);
        replay.keyframes.headYaw.insert(0, (double) yaw);
        replay.keyframes.bodyYaw.insert(0, (double) yaw);

        UIReplayList replays = this.panel.replayEditor.replaysList.replays;

        replays.refreshReplayList();
        replays.update();
        this.panel.replayEditor.setReplay(replay);
        replays.scrollToReplay(replay);

        /* The replay LIST changed, so the entity for the new actor has to be built — without this
         * the replay exists in the data but nothing shows up in the world. */
        this.panel.getController().createEntities();
        this.panel.replayEditor.updateChannelsList();

        this.removeFromParent();
    }

    /**
     * Where the new actor goes: on the block the camera looks at, or — when that misses, or when
     * {@code BBSSettings.editorQuickReplaySnapToBlock} is off — five blocks straight ahead.
     * Both branches are {@code UIReplayList.addReplay()}'s own.
     */
    private Vector3d getSpawnPosition(Camera camera)
    {
        Vector3d position = new Vector3d();
        Level world = Minecraft.getInstance().level;

        if (world != null && BBSSettings.editorQuickReplaySnapToBlock.get())
        {
            BlockHitResult hit = RayTracing.rayTrace(world, camera, 64F);

            if (hit.getType() != HitResult.Type.MISS)
            {
                Vec3 p = hit.getLocation();

                return position.set(p.x, p.y, p.z);
            }
        }

        return position.set(camera.getLookDirection()).mul(5F).add(camera.position);
    }
}
