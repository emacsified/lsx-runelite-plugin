package ge.lsx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Side panel: connection status, your live GE slots, recently logged trades, top flips and alerts.
 * Data comes from one /api/plugin/summary call per minute.
 */
class LsxPanel extends PluginPanel
{
	private static final Color GOLD = new Color(0xE0B341);
	private static final Color UP = new Color(0x2FBF71);
	private static final Color DOWN = new Color(0xEF5350);

	private final LsxPlugin plugin;
	private final String defaultWebUrl;
	private final JLabel status = new JLabel();
	private final JPanel content = new JPanel();
	private final JButton openWeb = new JButton("Open LSX");
	private String webUrl;

	@Inject
	LsxPanel(LsxPlugin plugin, ApiClient api)
	{
		super(false);
		this.plugin = plugin;
		this.defaultWebUrl = api.webUrl();
		this.webUrl = defaultWebUrl;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setOpaque(false);
		JLabel title = new JLabel("Lumbridge Stock Exchange");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(GOLD);
		header.add(title, BorderLayout.NORTH);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(status, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setOpaque(false);
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> plugin.refreshPanel());
		openWeb.addActionListener(e ->
		{
			if (webUrl != null)
			{
				LinkBrowser.browse(webUrl);
			}
		});
		openWeb.setEnabled(true);
		buttons.add(openWeb);
		buttons.add(refresh);
		header.add(buttons, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		JPanel wrap = new ScrollableColumn();
		wrap.add(content, BorderLayout.NORTH);
		// The header stays put and everything below it scrolls. Without this the panel asks for its full
		// content height and RuneLite grows the client window to fit it.
		JScrollPane scroll = new JScrollPane(wrap, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(new EmptyBorder(6, 0, 0, 0));
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(0, 0)); // take whatever height is left, never more
		add(scroll, BorderLayout.CENTER);
		showStatus("Loading…", false);
	}

	/** Scroll view that always fits the sidebar's width, so rows wrap instead of scrolling sideways. */
	private static final class ScrollableColumn extends JPanel implements Scrollable
	{
		ScrollableColumn()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return Math.max(16, visible.height - 32);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	void showStatus(String text, boolean error)
	{
		status.setText(html(text));
		status.setForeground(error ? DOWN : ColorScheme.LIGHT_GRAY_COLOR);
		if (error)
		{
			content.removeAll();
			content.revalidate();
			content.repaint();
		}
	}

	void showSignedOut(String site)
	{
		webUrl = site;
		openWeb.setEnabled(true);
		showStatus("Prices and overlays are on. To log trades and see your GE slots and alerts, sign in at "
			+ site.replaceFirst("^https?://", "") + ", create a token under Account → API tokens, and paste it in the plugin settings.", false);
		content.removeAll();
		content.revalidate();
		content.repaint();
	}

	void update(JsonObject s)
	{
		JsonObject user = s.getAsJsonObject("user");
		String who = user != null && user.has("display_name") ? user.get("display_name").getAsString() : "?";
		JsonObject ent = s.has("entitlements") && s.get("entitlements").isJsonObject() ? s.getAsJsonObject("entitlements") : null;
		boolean members = ent != null && ent.has("members_items") && ent.get("members_items").getAsBoolean();
		showStatus("Connected as " + who + (members ? "" : " · F2P access (members items hidden)"), false);
		webUrl = s.has("web_url") && !s.get("web_url").isJsonNull() ? s.get("web_url").getAsString() : defaultWebUrl;
		openWeb.setEnabled(true);

		content.removeAll();

		section("Your GE slots");
		JsonArray slots = s.getAsJsonArray("slots");
		if (slots == null || slots.size() == 0)
		{
			note("No offers tracked yet. Turn on 'Report GE trades' in the plugin settings.");
		}
		else
		{
			for (JsonElement el : slots)
			{
				JsonObject o = el.getAsJsonObject();
				boolean buy = "buy".equals(str(o, "side"));
				String right = num(o, "filled_qty") + "/" + num(o, "total_qty") + " @ " + Format.gp(num(o, "price"));
				row((buy ? "Buy " : "Sell ") + str(o, "name"), right, buy ? UP : GOLD, itemLink(o));
			}
		}

		section("Recent trades");
		JsonArray trades = s.getAsJsonArray("recent_trades");
		if (trades == null || trades.size() == 0)
		{
			note("Nothing logged yet.");
		}
		else
		{
			for (JsonElement el : trades)
			{
				JsonObject t = el.getAsJsonObject();
				boolean buy = "buy".equals(str(t, "side"));
				row((buy ? "+" : "-") + Format.full(num(t, "qty")) + " " + str(t, "name"),
					Format.gp(num(t, "price")), buy ? UP : GOLD, itemLink(t));
			}
		}

		section("Top flips now");
		JsonArray flips = s.getAsJsonArray("top_flips");
		if (flips == null || flips.size() == 0)
		{
			note("No reliable flips right now.");
		}
		else
		{
			for (JsonElement el : flips)
			{
				JsonObject f = el.getAsJsonObject();
				row(str(f, "name"), "+" + Format.gp(num(f, "profit")) + " ea", UP, itemLink(f));
			}
		}

		section("Recent alerts");
		JsonArray alerts = s.getAsJsonArray("alerts");
		if (alerts == null || alerts.size() == 0)
		{
			note("No alerts. Create them on LSX's Alerts page.");
		}
		else
		{
			for (JsonElement el : alerts)
			{
				note(str(el.getAsJsonObject(), "message"));
			}
		}

		content.revalidate();
		content.repaint();
	}

	private String itemLink(JsonObject o)
	{
		return webUrl != null && o.has("item_id") ? webUrl + "/#/item/" + o.get("item_id").getAsInt() : null;
	}

	private void section(String name)
	{
		content.add(Box.createRigidArea(new Dimension(0, 10)));
		JLabel l = new JLabel(name);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setAlignmentX(LEFT_ALIGNMENT);
		content.add(l);
		content.add(Box.createRigidArea(new Dimension(0, 4)));
	}

	private void note(String text)
	{
		JLabel l = new JLabel(html(text));
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(2, 0, 2, 0));
		content.add(l);
	}

	private void row(String left, String right, Color rightColor, String link)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		r.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		r.setAlignmentX(LEFT_ALIGNMENT);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JLabel l = new JLabel(left);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.WHITE);
		JLabel v = new JLabel(right, SwingConstants.RIGHT);
		v.setFont(FontManager.getRunescapeSmallFont());
		v.setForeground(rightColor);
		r.add(l, BorderLayout.CENTER);
		r.add(v, BorderLayout.EAST);
		if (link != null)
		{
			r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			r.setToolTipText("Open on LSX");
			r.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LinkBrowser.browse(link);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					r.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			});
		}
		content.add(r);
		content.add(Box.createRigidArea(new Dimension(0, 2)));
	}

	private static String str(JsonObject o, String k)
	{
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "?";
	}

	private static Long num(JsonObject o, String k)
	{
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : null;
	}

	private static String html(String text)
	{
		String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width:190px'>" + safe + "</body></html>";
	}
}
