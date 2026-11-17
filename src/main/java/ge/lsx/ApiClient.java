package ge.lsx;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talks to the user's own LSX API. All calls are asynchronous (OkHttp threads); callers
 * must hop to the client thread / EDT themselves before touching game state or Swing.
 */
@Slf4j
@Singleton
public class ApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	/** The LSX service this plugin talks to. */
	static final String LSX_URL = "https://lsx.ge";
	/**
	 * Developer override, never shown in the UI: run a dev client with -Dlsx.apiUrl=http://127.0.0.1:8077
	 * to test against a local server.
	 */
	static final String DEV_OVERRIDE_PROPERTY = "lsx.apiUrl";

	private final OkHttpClient http;
	private final Gson gson;
	private final LsxConfig config;
	private final HttpUrl base;

	private volatile String pricesEtag;
	private volatile String lastError;

	@Inject
	ApiClient(OkHttpClient http, Gson gson, LsxConfig config)
	{
		this(http, gson, config, System.getProperty(DEV_OVERRIDE_PROPERTY, LSX_URL));
	}

	ApiClient(OkHttpClient http, Gson gson, LsxConfig config, String baseUrl)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		HttpUrl parsed = parseBase(baseUrl);
		this.base = parsed != null ? parsed : HttpUrl.parse(LSX_URL);
	}

	static HttpUrl parseBase(String raw)
	{
		String s = raw == null ? "" : raw.trim();
		if (s.isEmpty())
		{
			return null;
		}
		if (!s.startsWith("http://") && !s.startsWith("https://"))
		{
			s = "https://" + s;
		}
		return HttpUrl.parse(s.replaceAll("/+$", ""));
	}

	HttpUrl base()
	{
		return base;
	}

	/** The LSX website, for "open in browser" links. */
	String webUrl()
	{
		return base.toString().replaceAll("/+$", "");
	}

	boolean configured()
	{
		return base != null;
	}

	boolean hasToken()
	{
		return config.apiToken() != null && !config.apiToken().trim().isEmpty();
	}

	String lastError()
	{
		return lastError;
	}

	void resetPricesCache()
	{
		pricesEtag = null;
	}

	HttpUrl url(String path)
	{
		// HttpUrl renders a bare host with a trailing "/", so trim it before appending "/api/..."
		String base = base().toString().replaceAll("/+$", "");
		return HttpUrl.parse(base + path);
	}

	private Request.Builder request(String path)
	{
		Request.Builder b = new Request.Builder().url(url(path)).header("Accept", "application/json");
		if (hasToken())
		{
			b.header("Authorization", "Bearer " + config.apiToken().trim());
		}
		return b;
	}

	void get(String path, Consumer<JsonObject> onOk, Consumer<String> onError)
	{
		if (!configured())
		{
			onError.accept("LSX address is invalid");
			return;
		}
		enqueue(request(path).get().build(), onOk, onError);
	}

	void post(String path, Object body, Consumer<JsonObject> onOk, Consumer<String> onError)
	{
		if (!configured())
		{
			onError.accept("LSX address is invalid");
			return;
		}
		RequestBody rb = RequestBody.create(JSON, gson.toJson(body));
		enqueue(request(path).post(rb).build(), onOk, onError);
	}

	private void enqueue(Request req, Consumer<JsonObject> onOk, Consumer<String> onError)
	{
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				lastError = "Can't reach LSX: " + e.getMessage();
				log.debug("LSX request failed: {}", req.url(), e);
				onError.accept(lastError);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					String text = body == null ? "" : body.string();
					if (!response.isSuccessful())
					{
						lastError = describe(response.code(), text);
						onError.accept(lastError);
						return;
					}
					lastError = null;
					onOk.accept(text.isEmpty() ? new JsonObject() : gson.fromJson(text, JsonObject.class));
				}
				catch (Exception e)
				{
					lastError = "Bad response from LSX";
					log.debug("LSX bad response from {}", req.url(), e);
					onError.accept(lastError);
				}
			}
		});
	}

	private String describe(int code, String body)
	{
		switch (code)
		{
			case 401:
				return hasToken() ? "API token rejected (revoked or wrong?)" : "This needs an API token";
			case 403:
				return "Not allowed by LSX";
			case 404:
				return "Endpoint not found: is the API URL right?";
			default:
				return "LSX error " + code;
		}
	}

	/**
	 * Fetches the compact price snapshot. Uses ETags so an unchanged snapshot costs a 304.
	 * onOk receives null when nothing changed.
	 */
	void fetchPrices(Consumer<Map<Integer, PriceInfo>> onOk, Consumer<String> onError)
	{
		if (!configured())
		{
			onError.accept("LSX address is invalid");
			return;
		}
		Request.Builder b = request("/api/plugin/prices").get();
		String etag = pricesEtag;
		if (etag != null)
		{
			b.header("If-None-Match", etag);
		}
		http.newCall(b.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				lastError = "Can't reach LSX: " + e.getMessage();
				onError.accept(lastError);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (response.code() == 304)
					{
						onOk.accept(null);
						return;
					}
					if (!response.isSuccessful() || body == null)
					{
						lastError = describe(response.code(), "");
						onError.accept(lastError);
						return;
					}
					JsonObject root = gson.fromJson(body.string(), JsonObject.class);
					onOk.accept(parsePrices(root));
					pricesEtag = response.header("ETag");
					lastError = null;
				}
				catch (Exception e)
				{
					lastError = "Bad price data from LSX";
					log.debug("LSX price parse failed", e);
					onError.accept(lastError);
				}
			}
		});
	}

	static Map<Integer, PriceInfo> parsePrices(JsonObject root)
	{
		JsonArray items = root.getAsJsonArray("items");
		if (items == null)
		{
			return Collections.emptyMap();
		}
		Map<Integer, PriceInfo> out = new HashMap<>(items.size() * 2);
		for (JsonElement el : items)
		{
			JsonArray r = el.getAsJsonArray();
			// [id, high, low, profit, roi_bp, limit, consistency, vol_24h, highalch, members]
			int id = r.get(0).getAsInt();
			out.put(id, new PriceInfo(id, asLong(r, 1), asLong(r, 2), asLong(r, 3), asInt(r, 4),
				asInt(r, 5), asInt(r, 6), asLong(r, 7)));
		}
		return out;
	}

	private static Long asLong(JsonArray a, int i)
	{
		return i < a.size() && !a.get(i).isJsonNull() ? a.get(i).getAsLong() : null;
	}

	private static Integer asInt(JsonArray a, int i)
	{
		return i < a.size() && !a.get(i).isJsonNull() ? a.get(i).getAsInt() : null;
	}
}
