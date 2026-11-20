# Lumbridge Stock Exchange (LSX)

A RuneLite plugin for [lsx.ge](https://lsx.ge): live Grand Exchange margins in game, plus your LSX
portfolio, GE slots and alerts in the sidebar.

## Features

- **Item overlay:** flip profit after GE tax, margin, ROI or price on items in your inventory, and optionally your bank.
- **GE offer helper:** while you set up an offer, shows:
  - instant-buy and instant-sell prices, margin, profit after tax and fees, and ROI
  - the typical profit over the last 24 hours, when it differs from right now
  - buy limit, a realistic 4-hour potential and fill speed (how quickly a full buy limit trades)
  - margin consistency (how often the margin has actually been profitable recently) and 24h volume
  - a warning on 1–4gp spreads, where offers queue at the same price and fill slowly
  - with an API token: how many you hold, your average cost and your **break-even list price** after tax and fees
  - with an API token: how much of the **buy limit** you have left in the current 4-hour window, and when it resets (from the buys LSX has logged)
- **Offer warnings:** a chat message when one of your active offers falls behind the market (a buy below what sellers are taking, or a sell above what buyers are paying). The threshold is adjustable, and it works with or without trade logging.
- **Sidebar:** your live GE slots, recently logged trades, today's most reliable flips and your latest LSX alerts. Click any row to open the item on lsx.ge.
- **Trade logging** (opt-in): every GE fill is logged to your lsx.ge portfolio automatically, with profit after tax tracked for you.

All prices and calculations come from lsx.ge. The plugin only displays them.

## Setup

Prices and overlays work as soon as the plugin is installed. On a private LSX server they need an API token.

LSX accounts with free-to-play access see free-to-play items only, in the plugin as on the website.

To use your portfolio, slots and alerts:

1. Sign in at [lsx.ge](https://lsx.ge) with Discord.
2. Open **Account → API tokens** and create a token, for example "RuneLite".
3. Paste it into the plugin's **API token** setting.
4. Turn on **Report GE trades** if you want fills logged automatically.

## Privacy

This plugin talks only to `https://lsx.ge`.

| When | What is sent |
|---|---|
| Without a token | Only a request for the public price list, once a minute. Nothing about you or your account. Your IP address is visible to lsx.ge, as with any website. |
| With an API token | When you log in: your account hash (RuneLite's anonymous per-account ID), in-game name, account type, whether you're on a members world, and the plugin version. Then requests for your sidebar data (slots, trades, alerts, what you hold for break-even prices, and buy limits used), authenticated with your token. |
| With a token and **Report GE trades** on | For each GE slot as it changes: item, buy or sell, offer price, quantity, quantity filled and gp spent. |

Nothing else is collected, and nothing about you is sent until you paste in a token you created
yourself. Trade reporting is off until you switch it on. Revoke the token on lsx.ge at any time to cut
the plugin off. The full privacy policy is at https://lsx.ge/privacy.

## Development

Requires Java 11.

```bash
./gradlew build          # compile and run tests
./gradlew run            # launch RuneLite (developer mode) with the plugin loaded
./gradlew run -PlsxApi=http://127.0.0.1:8077   # point the dev client at a local LSX server
```

The `-PlsxApi` override sets the JVM property `lsx.apiUrl`. It's for development only and is
never shown in the plugin settings.

**Jagex accounts in the dev client:**

1. Add `--insecure-write-credentials` to RuneLite's client arguments in the Jagex Launcher.
2. Launch RuneLite once from the Jagex Launcher. This writes `~/.runelite/credentials.properties`.
3. `gradlew run` then picks those credentials up. Delete that file when you're done.

### How trade logging works

- **What RuneLite reports:** a `GrandExchangeOfferChanged` event with the slot's running totals (quantity filled so far and gp spent so far). It replays every slot when you log in or hop worlds.
- **Batching:** the plugin batches changes once per game tick, and ignores the bogus "empty slot" events the client fires while logging in or hopping.
- **Fill tracking on lsx.ge:** the server keeps the last snapshot of each slot and records only the newly filled quantity, at the average price of those fills. Replays never double-count.

## Plugin Hub submission

Plugin Hub entry (`plugins/lumbridge-stock-exchange` in
[runelite/plugin-hub](https://github.com/runelite/plugin-hub)):

```
repository=https://github.com/emacsified/lsx-runelite-plugin.git
commit=<full 40-character commit hash>
authors=emacsified
warning=This plugin submits your IP address and, if you enable trade reporting, your grand exchange offers and account name to a 3rd party website (lsx.ge) not controlled or verified by the RuneLite Developers.
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).
