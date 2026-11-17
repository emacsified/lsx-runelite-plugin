package ge.lsx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
