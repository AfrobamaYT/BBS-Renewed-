package mchorse.bbs_mod.ui.supporters;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Supplier;

public class UISupportersPanel extends UIDashboardPanel
{
    public UIElement ccSupporters;
    public UIElement superSupporters;
    public UIElement bbsEarlyAccessSupporters;

    private Supporters supporters = new Supporters();

    public UISupportersPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.supporters.setup();

        this.ccSupporters = new UIElement();
        this.ccSupporters.grid(5).items(3);
        this.superSupporters = new UIElement();
        this.superSupporters.grid(5).items(3);
        this.bbsEarlyAccessSupporters = new UIElement();
        this.bbsEarlyAccessSupporters.grid(5).items(3);

        UIScrollView scrollView = UI.scrollView(0, 0);
        UIElement column = UI.column(5, 10);

        scrollView.relative(this).full();

        /* Resources */
        UIButton tutorials = new UIButton(UIKeys.SUPPORTERS_TUTORIALS, (b) -> UIUtils.openWebLink(UIKeys.SUPPORTERS_TUTORIALS_LINK.get()));
        UIButton community = new UIButton(UIKeys.SUPPORTERS_COMMUNITY, (b) -> UIUtils.openWebLink(UIKeys.SUPPORTERS_COMMUNITY_LINK.get()));
        UIButton wiki = new UIButton(UIKeys.SUPPORTERS_WIKI, (b) -> UIUtils.openWebLink(UIKeys.SUPPORTERS_WIKI_LINK.get()));
        UIButton donate = new UIButton(UIKeys.SUPPORTERS_DONATE, (b) -> UIUtils.openWebLink(UIKeys.SUPPORTERS_DONATE_LINK.get()));

        Supplier<Integer> color = () -> BBSSettings.primaryColor(Colors.A50);

        column.add(new UIText().text(UIKeys.SUPPORTERS_INTRO).marginTop(20));
        column.add(UI.row(tutorials, community, wiki).marginBottom(12));
        column.add(new UIText().text(UIKeys.SUPPORTERS_CALL_TO_ACTION));
        column.add(donate.marginBottom(12));
        column.add(new UIText().text(UIKeys.SUPPORTERS_GRATITUDE));
        column.add(UI.label(UIKeys.SUPPORTERS_CC).background(color).marginTop(12).marginBottom(6));
        column.add(this.ccSupporters);
        column.add(UI.label(UIKeys.SUPPORTERS_SUPER_SUPPORTERS).background(color).marginTop(12).marginBottom(6));
        column.add(this.superSupporters);
        column.add(UI.label(UIKeys.SUPPORTERS_EARLY_ACCESS).background(color).marginTop(12).marginBottom(6));
        column.add(this.bbsEarlyAccessSupporters.marginBottom(12));
        column.w(500);

        UIElement row = UI.row(0, 0, new UIElement(), column, new UIElement());

        /* Fill in */

        for (Supporter supporter : this.supporters.getCCSupporters())
        {
            this.ccSupporters.add(this.createSupporter(supporter));
        }

        for (Supporter supporter : this.supporters.getSuperSupporters())
        {
            this.superSupporters.add(this.createSupporter(supporter));
        }

        for (Supporter supporter : this.supporters.getBBSEarlyAccessSupporters())
        {
            this.bbsEarlyAccessSupporters.add(this.createSupporter(supporter));
        }

        scrollView.add(row);

        this.add(scrollView);
    }

    public UIElement createSupporter(Supporter supporter)
    {
        if (supporter.hasOnlyName())
        {
            return UI.label(IKey.raw(supporter.name), Batcher2D.getDefaultTextRenderer().getHeight() + 4).labelAnchor(0F, 0.5F);
        }
        else if (supporter.hasNoBanner())
        {
            return UISupporterBanner.createLinkEntry(supporter);
        }

        return new UISupporterBanner(supporter);
    }
}