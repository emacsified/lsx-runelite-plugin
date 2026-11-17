package ge.lsx;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws a small number (profit, margin, ROI or a price) on items in the inventory and bank.
 * All numbers come from the tracker server; nothing is calculated here except formatting.
 */
class ItemValueOverlay extends WidgetItemOverlay
{
	private static final int BANK_GROUP = 12;
	private static final Color POSITIVE = new Color(0x2FBF71);
	private static final Color NEGATIVE = new Color(0xEF5350);
	private static final Color NEUTRAL = new Color(0xE0B341);

	private final LsxPlugin plugin;
	private final LsxConfig config;
	private final ItemManager itemManager;

	@Inject
	ItemValueOverlay(LsxPlugin plugin, LsxConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		boolean inBank = widgetItem.getWidget() != null && (widgetItem.getWidget().getId() >>> 16) == BANK_GROUP;
		if (inBank ? !config.bankOverlay() : !config.inventoryOverlay())
		{
			return;
		}
		PriceInfo p = plugin.price(itemManager.canonicalize(itemId));
		if (p == null)
		{
			return;
		}

		String text;
		Color color = NEUTRAL;
		switch (config.overlayValue())
		{
			case PROFIT:
				if (p.profit == null)
				{
					return;
				}
				text = Format.gp(p.profit);
				color = p.profit > 0 ? POSITIVE : p.profit < 0 ? NEGATIVE : NEUTRAL;
				break;
			case MARGIN:
				text = Format.gp(p.margin());
				break;
			case ROI:
				if (p.roiBp == null)
				{
					return;
				}
				text = Format.pct(p.roiBp);
				color = p.roiBp > 0 ? POSITIVE : p.roiBp < 0 ? NEGATIVE : NEUTRAL;
				break;
			case SELL_PRICE:
				text = Format.gp(p.low);
				break;
			case BUY_PRICE:
			default:
				text = Format.gp(p.high);
				break;
		}
		if (text == null || "-".equals(text))
		{
			return;
		}

		Rectangle b = widgetItem.getCanvasBounds();
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int x = b.x + b.width - fm.stringWidth(text);
		int y = b.y + b.height;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}
}
