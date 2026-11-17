package ge.lsx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * While a GE offer is being set up, shows the tracker's numbers for that item.
 */
class GeHelperOverlay extends OverlayPanel
{
	// Raw ids: GE interface group and the "item in the offer being set up" varp.
	private static final int GE_INTERFACE_GROUP = 465;
	private static final int VARP_CURRENT_GE_ITEM = 1151;

	private static final Color GOLD = new Color(0xE0B341);
	private static final Color UP = new Color(0x2FBF71);
	private static final Color DOWN = new Color(0xEF5350);

	private final Client client;
	private final LsxPlugin plugin;
	private final LsxConfig config;
	private final ItemManager itemManager;

	@Inject
	GeHelperOverlay(Client client, LsxPlugin plugin, LsxConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.geHelper())
		{
			return null;
		}
		Widget ge = client.getWidget(GE_INTERFACE_GROUP, 0);
		if (ge == null || ge.isHidden())
		{
			return null;
		}
		int itemId = client.getVarpValue(VARP_CURRENT_GE_ITEM);
		if (itemId <= 0)
		{
			return null;
		}
		PriceInfo p = plugin.price(itemId);
		String name = itemManager.getItemComposition(itemId).getName();

		panelComponent.getChildren().add(TitleComponent.builder().text(name).color(GOLD).build());
		if (p == null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("No recent trades").build());
			return super.render(graphics);
		}
		line("Buy at (insta-sell)", Format.full(p.low), Color.WHITE);
		line("Sell at (insta-buy)", Format.full(p.high), Color.WHITE);
		line("Margin", Format.full(p.margin()), Color.WHITE);
		if (p.profit != null)
		{
			line("Profit after tax", Format.full(p.profit), p.profit > 0 ? UP : DOWN);
		}
		line("ROI", Format.pct(p.roiBp), p.roiBp != null && p.roiBp > 0 ? UP : Color.WHITE);
		if (p.limit != null)
		{
			line("Buy limit / 4h", Format.full(p.limit.longValue()), Color.WHITE);
			if (p.profit != null && p.profit > 0)
			{
				line("Profit per limit", Format.gp(p.profit * p.limit), UP);
			}
		}
		if (p.consistency != null)
		{
			line("Margin consistency", p.consistency + "%",
				p.consistency >= 75 ? UP : p.consistency >= 45 ? Color.WHITE : DOWN);
		}
		line("24h volume", Format.gp(p.volume24h), Color.WHITE);
		return super.render(graphics);
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(rightColor).build());
	}
}
