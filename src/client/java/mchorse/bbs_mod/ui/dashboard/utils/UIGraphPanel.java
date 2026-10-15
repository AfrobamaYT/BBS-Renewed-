package mchorse.bbs_mod.ui.dashboard.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

public class UIGraphPanel extends UIDashboardPanel
{
    /** Flaechenkopf 26, Formelzeile 30 — die Masse aus der Formensprache. */
    private static final int KOPF = 26;
    private static final int FORMEL = 30;

    public UIGraphCanvas canvas;
    public UITextbox expression;
    public UIIcon help;

    public UIGraphPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.canvas = new UIGraphCanvas();
        this.expression = new UITextbox(10000, this.canvas::parseExpression);
        this.help = new UIIcon(Icons.HELP, (b) -> UIUtils.openWebLink("https://github.com/mchorse/aperture/wiki/Math-Expressions"));
        this.help.tooltip(UIKeys.GRAPH_HELP, Direction.TOP);

        String first = "sin(x)";

        this.expression.setText(first);
        this.canvas.parseExpression(first);

        /* Design-System, Muster E: Flaechenkopf oben (26 px, --header), Zeichenflaeche
         * dazwischen, Formelzeile als eigene Leiste unten (30 px) — statt eines Textfeldes,
         * das ueber dem Graphen schwebt. Ein Editor-Typ-Waehler waere hier eine Attrappe:
         * diese Flaeche schaltet auf nichts um, also steht dort nur der Titel. */
        this.canvas.relative(this).x(0).y(KOPF).w(1F).h(1F, -(KOPF + FORMEL));
        this.expression.relative(this).x(30).y(1F, -(FORMEL - 5)).w(1F, -40).h(20);
        this.help.relative(this.expression).x(1F, -19).y(1).wh(18, 18);

        this.expression.add(this.help);
        this.add(new UIRenderable(this::zeichneRahmen), this.canvas, this.expression);
    }

    /** Kopf- und Fussleiste nach der Tiefen-Leiter; die Zeichenflaeche bleibt dazwischen. */
    private void zeichneRahmen(UIContext context)
    {
        int kopf = BBSSettings.chromeSurface();
        int kante = BBSSettings.dividerColor();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + KOPF, kopf);
        context.batcher.box(this.area.x, this.area.y + KOPF - 1, this.area.ex(), this.area.y + KOPF, kante);
        context.batcher.text(UIKeys.GRAPH_TOOLTIP.get(), this.area.x + 10, this.area.y + 9, Colors.A100 | com.mcstudio.nano.NanoDesign.TXT);

        int oben = this.area.ey() - FORMEL;

        context.batcher.box(this.area.x, oben, this.area.ex(), this.area.ey(), kopf);
        context.batcher.box(this.area.x, oben, this.area.ex(), oben + 1, kante);
        context.batcher.text("f(x)", this.area.x + 8, oben + 11, Colors.A100 | com.mcstudio.nano.NanoDesign.TXT_OFF);
    }
}
