package ge.lsx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Lumbridge Stock Exchange",
	description = "LSX: margins on your items, flip suggestions, and optional auto-logging of GE trades to your LSX portfolio",
	tags = {"lsx", "grand exchange", "ge", "flipping", "prices", "margins", "merching", "stock exchange"}
)
public class LsxPlugin extends Plugin
{
	static final String VERSION = "1.1.0";

	// Raw ids rather than the deprecated Varbits/VarPlayer constants, to stay stable across API changes.
	private static final int VARBIT_ACCOUNT_TYPE = 1777;
	private static final String[] ACCOUNT_TYPES = {
		"normal", "ironman", "ultimate_ironman", "hardcore_ironman",
		"group_ironman", "hardcore_group_ironman", "unranked_group_ironman"
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LsxConfig config;

	@Inject
	private ApiClient api;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemValueOverlay itemOverlay;

	@Inject
	private GeHelperOverlay geHelperOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ItemManager itemManager;

	private LsxPanel panel;
	private NavigationButton navButton;

	/** Latest prices from the tracker, swapped atomically. */
	private volatile Map<Integer, PriceInfo> prices = Collections.emptyMap();
	/** What your LSX portfolio says you hold (with break-even list prices), from the panel summary. */
	private volatile Map<Integer, Holding> holdings = Collections.emptyMap();
	private volatile boolean membersAccess;
	private volatile boolean summaryLoaded;
	/** Slot -> the offer we last warned about, so each falling-behind offer is mentioned once. Client thread only. */
	private final Map<Integer, String> warnedOffers = new HashMap<>();

	/** Offer snapshots waiting to be sent, keyed by slot. Client thread only. */
	private final Map<Integer, Map<String, Object>> pendingOffers = new LinkedHashMap<>();
	private boolean sessionPending;
	private String accountName;

	@Provides
	LsxConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LsxConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(itemOverlay);
		overlayManager.add(geHelperOverlay);
		panel = injector.getInstance(LsxPanel.class);
		navButton = NavigationButton.builder()
			.tooltip("Lumbridge Stock Exchange")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		refreshPrices();
		refreshPanel();
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				sessionPending = true;
				queueAllOffers();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(itemOverlay);
		overlayManager.remove(geHelperOverlay);
		clientToolbar.removeNavigation(navButton);
		pendingOffers.clear();
		warnedOffers.clear();
		prices = Collections.emptyMap();
		holdings = Collections.emptyMap();
		summaryLoaded = false;
		panel = null;
	}

	PriceInfo price(int itemId)
	{
		return prices.get(itemId);
	}

	Holding holding(int itemId)
	{
		return holdings.get(itemId);
	}

	boolean membersAccess()
	{
		return membersAccess;
	}

	boolean hasSummary()
	{
		return summaryLoaded;
	}

	// ------------------------------------------------------------------ game events

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			sessionPending = true;
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			pendingOffers.clear();
			warnedOffers.clear();
			accountName = null;
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged e)
	{
		GrandExchangeOffer offer = e.getOffer();
		if (offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			warnedOffers.remove(e.getSlot());
		}
		if (!config.reportOffers())
		{
			return;
		}
		// The client clears all slots to EMPTY while logging in / hopping; those aren't real changes.
		if (client.getGameState() != GameState.LOGGED_IN && offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return;
		}
		pendingOffers.put(e.getSlot(), snapshot(e.getSlot(), offer));
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (sessionPending)
		{
			Player me = client.getLocalPlayer();
			if (me != null && me.getName() != null && client.getAccountHash() != -1)
			{
				sessionPending = false;
				accountName = me.getName();
				sendSession();
			}
		}
		flushOffers();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!LsxConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		if ("apiToken".equals(e.getKey()))
		{
			api.resetPricesCache();
			refreshPrices();
			refreshPanel();
			clientThread.invoke(() -> sessionPending = client.getGameState() == GameState.LOGGED_IN);
		}
		if ("reportOffers".equals(e.getKey()) && config.reportOffers())
		{
			// Just switched on: send the current state of every slot so in-progress offers are tracked.
			clientThread.invoke(this::queueAllOffers);
		}
	}

	// ------------------------------------------------------------------ offer reporting

	private void queueAllOffers()
	{
		if (!config.reportOffers() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		for (int slot = 0; slot < offers.length; slot++)
		{
			if (offers[slot] != null)
			{
				pendingOffers.put(slot, snapshot(slot, offers[slot]));
			}
		}
	}

	private static Map<String, Object> snapshot(int slot, GrandExchangeOffer o)
	{
		Map<String, Object> m = new HashMap<>();
		m.put("slot", slot);
		m.put("state", o.getState().name());
		m.put("item_id", o.getItemId());
		m.put("price", o.getPrice());
		m.put("total_qty", o.getTotalQuantity());
		m.put("filled_qty", o.getQuantitySold());
		m.put("spent", o.getSpent());
		return m;
	}

	/** Sends queued snapshots in one batch (called every tick, so login's burst of 8 events is one request). */
	private void flushOffers()
	{
		if (pendingOffers.isEmpty())
		{
			return;
		}
		long hash = client.getAccountHash();
		if (hash == -1 || client.getGameState() != GameState.LOGGED_IN)
		{
			return; // keep them until we know which account they belong to
		}
		if (!api.configured() || !api.hasToken())
		{
			pendingOffers.clear();
			return;
		}
		List<Map<String, Object>> batch = new ArrayList<>(pendingOffers.values());
		pendingOffers.clear();
		Map<String, Object> body = new HashMap<>();
		body.put("account_hash", Long.toString(hash));
		body.put("account_name", accountName);
		body.put("offers", batch);
		api.post("/api/plugin/offers", body, this::onFills,
			err -> log.debug("LSX: offer report failed: {}", err));
	}

	private void onFills(JsonObject resp)
	{
		if (!resp.has("fills") || resp.getAsJsonArray("fills").size() == 0)
		{
			return;
		}
		if (config.chatNotifications())
		{
			clientThread.invoke(() ->
			{
				for (JsonElement el : resp.getAsJsonArray("fills"))
				{
					JsonObject f = el.getAsJsonObject();
					String name = itemManager.getItemComposition(f.get("item_id").getAsInt()).getName();
					String verb = "buy".equals(f.get("side").getAsString()) ? "Bought" : "Sold";
					chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage("<col=e0b341>LSX</col>: logged " + verb.toLowerCase() + " "
							+ Format.full(f.get("qty").getAsLong()) + " x " + name + " @ "
							+ Format.full(f.get("price").getAsLong()) + " gp")
						.build());
				}
			});
		}
		refreshPanel();
	}

	private void sendSession()
	{
		if (!api.configured() || !api.hasToken())
		{
			return;
		}
		int type = client.getVarbitValue(VARBIT_ACCOUNT_TYPE);
		Map<String, Object> body = new HashMap<>();
		body.put("account_hash", Long.toString(client.getAccountHash()));
		body.put("name", accountName);
		body.put("account_type", type >= 0 && type < ACCOUNT_TYPES.length ? ACCOUNT_TYPES[type] : null);
		body.put("is_member", client.getWorldType().contains(WorldType.MEMBERS));
		body.put("plugin_version", VERSION);
		api.post("/api/plugin/session", body, r -> refreshPanel(),
			err -> log.debug("LSX: session report failed: {}", err));
	}

	// ------------------------------------------------------------------ background refresh

	@Schedule(period = 60, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void refreshPrices()
	{
		if (!api.configured())
		{
			return;
		}
		api.fetchPrices(map ->
		{
			if (map != null)
			{
				prices = map;
				log.debug("LSX: loaded {} prices", map.size());
			}
			clientThread.invoke(this::checkOffers);
		}, err -> log.debug("LSX: price refresh failed: {}", err));
	}

	@Schedule(period = 60, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void refreshPanel()
	{
		LsxPanel p = panel;
		if (p == null)
		{
			return;
		}
		if (!api.hasToken())
		{
			holdings = Collections.emptyMap();
			summaryLoaded = false;
			SwingUtilities.invokeLater(() -> p.showSignedOut(api.webUrl()));
			return;
		}
		api.get("/api/plugin/summary",
			summary ->
			{
				holdings = ApiClient.parseHoldings(summary);
				membersAccess = ApiClient.membersAccess(summary);
				summaryLoaded = true;
				SwingUtilities.invokeLater(() -> p.update(summary));
			},
			err -> SwingUtilities.invokeLater(() -> p.showStatus(err, true)));
	}

	// ------------------------------------------------------------------ offer warnings

	/** Chat warning for active offers priced too far from the market to fill. Client thread. */
	void checkOffers()
	{
		if (!config.offerWarnings() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		for (int slot = 0; slot < offers.length; slot++)
		{
			GrandExchangeOffer o = offers[slot];
			if (o == null)
			{
				continue;
			}
			boolean buying = o.getState() == GrandExchangeOfferState.BUYING;
			if (!buying && o.getState() != GrandExchangeOfferState.SELLING)
			{
				warnedOffers.remove(slot);
				continue;
			}
			Double behind = behindPercent(buying, o.getPrice(), prices.get(o.getItemId()));
			if (behind == null || behind < config.offerWarnPercent())
			{
				warnedOffers.remove(slot); // back in line with the market; warn again if it slips later
				continue;
			}
			String key = o.getItemId() + ":" + o.getPrice() + ":" + buying;
			if (key.equals(warnedOffers.get(slot)))
			{
				continue;
			}
			warnedOffers.put(slot, key);
			PriceInfo p = prices.get(o.getItemId());
			String name = itemManager.getItemComposition(o.getItemId()).getName();
			String text = buying
				? "your buy offer for " + name + " at " + Format.full((long) o.getPrice()) + " is "
					+ String.format("%.1f", behind) + "% under the market (sellers are taking " + Format.full(p.low) + "), so it may not fill"
				: "your sell offer for " + name + " at " + Format.full((long) o.getPrice()) + " is "
					+ String.format("%.1f", behind) + "% over the market (buyers are paying " + Format.full(p.high) + "), so it may not fill";
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage("<col=e0b341>LSX</col>: " + text)
				.build());
		}
	}

	/**
	 * How far (in %) an offer sits on the wrong side of the market: a buy below the instant-sell price,
	 * or a sell above the instant-buy price. Null when unknown or not behind.
	 */
	static Double behindPercent(boolean buying, long offerPrice, PriceInfo p)
	{
		if (p == null || offerPrice <= 0)
		{
			return null;
		}
		if (buying)
		{
			return p.low != null && p.low > offerPrice ? (p.low - offerPrice) * 100.0 / offerPrice : null;
		}
		return p.high != null && p.high > 0 && offerPrice > p.high ? (offerPrice - p.high) * 100.0 / p.high : null;
	}

	/** The LSX gold coin (same as the lsx.ge logo), drawn at sidebar size. */
	static BufferedImage icon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(5.6f, 4.8f), 10f,
			new float[]{0f, 0.35f, 0.75f, 1f},
			new Color[]{new Color(0xFFF3C4), new Color(0xF1C95A), new Color(0xC8952A), new Color(0x9C6F17)}));
		g.fill(new Ellipse2D.Float(0.5f, 0.5f, 15f, 15f));
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(new Color(120, 80, 10, 170));
		g.draw(new Ellipse2D.Float(1.1f, 1.1f, 13.8f, 13.8f));
		g.setStroke(new BasicStroke(0.8f));
		g.setColor(new Color(120, 80, 10, 110));
		g.draw(new Ellipse2D.Float(3.4f, 3.4f, 9.2f, 9.2f));
		g.dispose();
		return img;
	}
}
