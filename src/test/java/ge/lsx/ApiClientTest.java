package ge.lsx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.Test;

public class ApiClientTest
{
	@Test
	public void parsesCompactPriceRows()
	{
		String json = "{\"fields\":[],\"items\":[[4151,630000,626887,-9487,-151,70,50,5522,72000,true],"
			+ "[2,240,233,2,85,null,null,1000,null,false]]}";
		Map<Integer, PriceInfo> m = ApiClient.parsePrices(new Gson().fromJson(json, JsonObject.class));
		assertEquals(2, m.size());
		PriceInfo whip = m.get(4151);
		assertEquals(Long.valueOf(-9487), whip.profit);
		assertEquals(Long.valueOf(3113), whip.margin());
		assertEquals(Integer.valueOf(70), whip.limit);
		assertNull(m.get(2).limit);
	}

	@Test
	public void parsesV2FieldsAndFlagsCrowdedSpreads()
	{
		String json = "{\"items\":[[13190,10900000,10800000,-1090000,-1000,100,40,900,null,false,\"quick\",0,-1000000,1085000,true],"
			+ "[30000,14,13,1,769,300000,100,9000000,null,true,\"instant\",46656,1,0,false]]}";
		Map<Integer, PriceInfo> m = ApiClient.parsePrices(new Gson().fromJson(json, JsonObject.class));
		PriceInfo bond = m.get(13190);
		assertEquals("quick", bond.fillGrade);
		assertEquals(Long.valueOf(1085000), bond.resaleFee);
		assertTrue(bond.taxExempt);
		assertFalse(bond.crowded());
		PriceInfo essence = m.get(30000);
		assertTrue(essence.crowded());
		assertEquals(Long.valueOf(46656), essence.potential4h);
		assertEquals(Boolean.TRUE, essence.members);
	}

	@Test
	public void oldServerRowsStillParse()
	{
		PriceInfo p = ApiClient.parsePrices(new Gson().fromJson("{\"items\":[[2,240,233,2,85,null,null,1000]]}", JsonObject.class)).get(2);
		assertNull(p.fillGrade);
		assertNull(p.potential4h);
		assertFalse(p.taxExempt);
	}

	@Test
	public void parsesHoldingsAndTier()
	{
		JsonObject s = new Gson().fromJson("{\"entitlements\":{\"members_items\":false},\"holdings\":["
			+ "{\"item_id\":4151,\"open_qty\":20,\"avg_cost\":661536,\"break_even\":675036},"
			+ "{\"item_id\":2,\"open_qty\":5,\"avg_cost\":40,\"break_even\":null}]}", JsonObject.class);
		Map<Integer, Holding> h = ApiClient.parseHoldings(s);
		assertEquals(Long.valueOf(675036), h.get(4151).breakEven);
		assertEquals(20, h.get(4151).quantity);
		assertNull(h.get(2).breakEven);
		assertFalse(ApiClient.membersAccess(s));
		assertTrue(ApiClient.parseHoldings(new JsonObject()).isEmpty());
	}

	@Test
	public void parsesLimitUse()
	{
		JsonObject s = new Gson().fromJson("{\"limits\":[{\"item_id\":4151,\"used\":45,\"resets_at\":1795189013}]}", JsonObject.class);
		Map<Integer, LimitUse> l = ApiClient.parseLimits(s);
		assertEquals(45, l.get(4151).used);
		assertEquals(1795189013L, l.get(4151).resetsAt);
		assertEquals(Long.valueOf(25), l.get(4151).left(70));
		assertEquals(Long.valueOf(0), new LimitUse(1, 90, 0).left(70));
		assertNull(l.get(4151).left(null));
		assertTrue(ApiClient.parseLimits(new JsonObject()).isEmpty());
	}

	@Test
	public void offerBehindTheMarket()
	{
		PriceInfo p = new PriceInfo(1, 1100L, 1000L, 78L, 780, 100, 90, 5000L, false, "quick", 0L, 78L, 0L, false);
		assertEquals(5.0, LsxPlugin.behindPercent(true, 952, p) , 0.1);   // buying at 952 while sellers take 1,000
		assertNull(LsxPlugin.behindPercent(true, 1000, p));                // at the market: fine
		assertEquals(10.0, LsxPlugin.behindPercent(false, 1210, p), 0.01); // selling at 1,210 while buyers pay 1,100
		assertNull(LsxPlugin.behindPercent(false, 1100, p));
		assertNull(LsxPlugin.behindPercent(true, 952, null));
	}

	private static final LsxConfig CONFIG = new LsxConfig()
	{
	};

	@Test
	public void defaultsToLsxGe()
	{
		ApiClient c = new ApiClient(new okhttp3.OkHttpClient(), new Gson(), CONFIG, ApiClient.LSX_URL);
		assertEquals("https://lsx.ge/api/plugin/prices", c.url("/api/plugin/prices").toString());
		assertEquals("https://lsx.ge", c.webUrl());
	}

	@Test
	public void devOverrideBuildsUrlsWithoutDoubleSlash()
	{
		for (String override : new String[]{"http://127.0.0.1:8077", "http://127.0.0.1:8077/"})
		{
			ApiClient c = new ApiClient(new okhttp3.OkHttpClient(), new Gson(), CONFIG, override);
			assertEquals("http://127.0.0.1:8077/api/plugin/prices", c.url("/api/plugin/prices").toString());
		}
	}

	@Test
	public void invalidOverrideFallsBackToLsxGe()
	{
		ApiClient c = new ApiClient(new okhttp3.OkHttpClient(), new Gson(), CONFIG, "  ");
		assertEquals("https://lsx.ge/api/plugin/summary", c.url("/api/plugin/summary").toString());
	}

	@Test
	public void formatsGp()
	{
		assertEquals("950", Format.gp(950L));
		assertEquals("12.5k", Format.gp(12_500L));
		assertEquals("1.25m", Format.gp(1_250_000L));
		assertEquals("-2.1b", Format.gp(-2_100_000_000L));
		assertEquals("1.5%", Format.pct(150));
	}
}
