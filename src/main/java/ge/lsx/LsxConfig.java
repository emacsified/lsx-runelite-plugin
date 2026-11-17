package ge.lsx;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(LsxConfig.GROUP)
public interface LsxConfig extends Config
{
	String GROUP = "lsx";

	@ConfigSection(
		name = "Your LSX account",
		description = "Link the plugin to your account on lsx.ge for trade logging, your GE slots and alerts",
		position = 0
	)
	String connection = "connection";

	@ConfigSection(
		name = "Overlays",
		description = "Prices and margins shown in game",
		position = 1
	)
	String overlays = "overlays";

	@ConfigItem(
		keyName = "apiToken",
		name = "API token",
		description = "Personal token from lsx.ge → Account → API tokens (starts with get_). Needed for trade logging, your GE slots and alerts. Prices work without it.",
		secret = true,
		section = connection,
		position = 1
	)
	default String apiToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "reportOffers",
		name = "Report GE trades",
		description = "Send your Grand Exchange offers (item, price, quantity filled, gp spent), your account name and account type to lsx.ge so fills are logged in your portfolio automatically. Needs an API token. Off by default.",
		section = connection,
		position = 2
	)
	default boolean reportOffers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatNotifications",
		name = "Chat message on logged fill",
		description = "Show a chat message when a GE fill is logged to LSX",
		section = connection,
		position = 3
	)
	default boolean chatNotifications()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inventoryOverlay",
		name = "Inventory overlay",
		description = "Show a value on items in your inventory",
		section = overlays,
		position = 0
	)
	default boolean inventoryOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankOverlay",
		name = "Bank overlay",
		description = "Show a value on items in your bank",
		section = overlays,
		position = 1
	)
	default boolean bankOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayValue",
		name = "Overlay shows",
		description = "Which number to draw on items",
		section = overlays,
		position = 2
	)
	default OverlayValue overlayValue()
	{
		return OverlayValue.PROFIT;
	}

	@ConfigItem(
		keyName = "geHelper",
		name = "GE offer helper",
		description = "While setting up a GE offer, show current prices, profit after tax, buy limit and margin consistency",
		section = overlays,
		position = 3
	)
	default boolean geHelper()
	{
		return true;
	}
}
