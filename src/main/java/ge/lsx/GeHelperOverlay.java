package ge.lsx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * While a GE offer is being set up, shows LSX's numbers for that item, and your break-even list price
 * if your portfolio says you hold some.
 */
class GeHelperOverlay extends OverlayPanel
{
	// Raw ids: GE interface group and the "item in the offer being set up" varp.
	private static final int GE_INTERFACE_GROUP = 465;
	private static final int VARP_CURRENT_GE_ITEM = 1151;

	private static final Color GOLD = new Color(0xE0B341);
	private static final Color UP = new Color(0x2FBF71);
	private static final Color DOWN = new Color(0xEF5350);
	private static final Color WARN = new Color(0xE0A341);

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
		ItemComposition item = itemManager.getItemComposition(itemId);

		panelComponent.getChildren().add(TitleComponent.builder().text(item.getName()).color(GOLD).build());
		if (p == null)
		{
			boolean lockedOut = item.isMembers() && plugin.hasSummary() && !plugin.membersAccess();
			panelComponent.getChildren().add(LineComponent.builder()
				.left(lockedOut ? "Members item: your LSX account has F2P access" : "No recent trades").build());
			return super.render(graphics);
		}
		line("Buy at (insta-sell)", Format.full(p.low), Color.WHITE);
		line("Sell at (insta-buy)", Format.full(p.high), Color.WHITE);
		line("Margin", Format.full(p.margin()), Color.WHITE);
		if (p.profit != null)
		{
			line("Profit after tax & fees", Format.full(p.profit), p.profit > 0 ? UP : DOWN);
		}
		if (p.typicalProfit != null && p.profit != null && Math.abs(p.typicalProfit - p.profit) > Math.max(1, Math.abs(p.profit) / 5))
		{
			line("Typical (24h)", Format.full(p.typicalProfit), p.typicalProfit > 0 ? Color.WHITE : DOWN);
		}
		line("ROI", Format.pct(p.roiBp), p.roiBp != null && p.roiBp > 0 ? UP : Color.WHITE);
		LimitUse used = plugin.limitUse(itemId);
		if (used != null)
		{
			Long left = used.left(p.limit);
			long mins = Math.max(0, (used.resetsAt - System.currentTimeMillis() / 1000) / 60);
			String reset = mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m" : mins + "m";
			line("Buy limit left", left != null ? Format.full(left) + " / " + Format.full(p.limit.longValue()) : Format.full(used.used) + " bought",
				left != null && left == 0 ? DOWN : Color.WHITE);
			line("Limit resets in", reset, Color.WHITE);
		}
		else if (p.limit != null)
		{
			line("Buy limit / 4h", Format.full(p.limit.longValue()), Color.WHITE);
		}
		if (p.potential4h != null && p.potential4h > 0)
		{
			line("4h potential", Format.gp(p.potential4h), UP);
		}
		if (p.fillGrade != null)
		{
			line("Fill speed", fillLabel(p.fillGrade), fillColor(p.fillGrade));
		}
		if (p.consistency != null)
		{
			line("Margin consistency", p.consistency + "%",
				p.consistency >= 75 ? UP : p.consistency >= 45 ? Color.WHITE : DOWN);
		}
		line("24h volume", Format.gp(p.volume24h), Color.WHITE);
		if (p.resaleFee != null && p.resaleFee > 0)
		{
			line("Resale fee each", Format.full(p.resaleFee), WARN);
		}
		if (p.crowded())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(p.margin() + "gp spread: offers queue at the same price and fill slowly").leftColor(WARN).build());
		}

		Holding h = plugin.holding(itemId);
		if (h != null)
		{
			line("You hold", Format.full(h.quantity) + " @ " + Format.full(h.averageCost), Color.WHITE);
			if (h.breakEven != null)
			{
				boolean above = p.high != null && p.high >= h.breakEven;
				line("Break-even list", Format.full(h.breakEven), above ? UP : DOWN);
			}
		}
		return super.render(graphics);
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(rightColor).build());
	}

	static String fillLabel(String grade)
	{
		return grade.isEmpty() ? grade : Character.toUpperCase(grade.charAt(0)) + grade.substring(1);
	}

	private static Color fillColor(String grade)
	{
		switch (grade)
		{
			case "instant":
			case "quick":
				return UP;
			case "steady":
				return Color.WHITE;
			default:
				return DOWN;
		}
	}
}
