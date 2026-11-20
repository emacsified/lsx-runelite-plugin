package ge.lsx;

import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

public class LsxPanelTest
{
	private static final LsxConfig CONFIG = new LsxConfig()
	{
	};

	@Test
	public void longContentScrollsInsteadOfGrowingThePanel()
	{
		ApiClient api = new ApiClient(new okhttp3.OkHttpClient(), new Gson(), CONFIG, ApiClient.LSX_URL);
		LsxPanel panel = new LsxPanel(new LsxPlugin(), api);
		int empty = panel.getPreferredSize().height;

		JsonObject s = new JsonObject();
		JsonObject user = new JsonObject();
		user.addProperty("display_name", "Tester");
		s.add("user", user);
		JsonArray slots = new JsonArray(), trades = new JsonArray(), flips = new JsonArray(), alerts = new JsonArray();
		for (int i = 0; i < 8; i++)
		{
			JsonObject o = new JsonObject();
			o.addProperty("item_id", 4151);
			o.addProperty("name", "Abyssal whip");
			o.addProperty("side", "buy");
			o.addProperty("price", 1_500_000);
			o.addProperty("total_qty", 70);
			o.addProperty("filled_qty", 10);
			o.addProperty("qty", 10);
			o.addProperty("profit", 12_000);
			o.addProperty("message", "Abyssal whip dropped below 1.4m, a long alert message that wraps over lines");
			slots.add(o);
			trades.add(o);
			flips.add(o);
			alerts.add(o);
		}
		s.add("slots", slots);
		s.add("recent_trades", trades);
		s.add("top_flips", flips);
		s.add("alerts", alerts);
		panel.update(s);

		// 32 rows plus headings would be well over 800px tall; the panel itself must not ask for that.
		int full = panel.getPreferredSize().height;
		assertTrue("panel grew to " + full + "px (was " + empty + ")", full < 250);
	}
}
