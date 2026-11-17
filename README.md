# Lumbridge Stock Exchange (LSX)

A RuneLite plugin for [lsx.ge](https://lsx.ge): live Grand Exchange margins in game, plus your LSX
portfolio, GE slots and alerts in the sidebar.

## Features

- **Item overlay:** flip profit after GE tax, margin, ROI or price on items in your inventory, and optionally your bank.
- **GE offer helper:** while you set up an offer, shows:
  - instant-buy and instant-sell prices, margin, profit after tax and ROI
  - buy limit and profit per limit
  - margin consistency (how often the margin has actually been profitable recently)
  - 24h volume
- **Sidebar:** your live GE slots, recently logged trades, today's most reliable flips and your latest LSX alerts. Click any row to open the item on lsx.ge.
- **Trade logging** (opt-in): every GE fill is logged to your lsx.ge portfolio automatically, with profit after tax tracked for you.

All prices and calculations come from lsx.ge. The plugin only displays them.

## Setup

Prices and overlays work as soon as the plugin is installed.

To use your portfolio, slots and alerts:

1. Sign in at [lsx.ge](https://lsx.ge) with Discord.
2. Open **Account → API tokens** and create a token, for example "RuneLite".
3. Paste it into the plugin's **API token** setting.
4. Turn on **Report GE trades** if you want fills logged automatically.

## Privacy

This plugin talks only to `https://lsx.ge`.

| When | What is sent |
|---|---|
| Always (prices) | A request for the public price list, once a minute. Your IP address is visible to lsx.ge, as with any website. |
| With an API token | Requests for your sidebar data, authenticated with your token. |
| With **Report GE trades** on | For each GE slot: item, offer price, quantity, quantity filled and gp spent. Also your account hash (RuneLite's anonymous per-account ID), in-game name, account type and whether you're on a members world. |

Nothing else is collected. Trade reporting is off until you switch it on, and needs a token you
created yourself. Revoke the token on lsx.ge at any time to cut the plugin off.

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
