package ge.lsx;

/**
 * One item's market numbers, computed by the tracker server (profit already includes GE tax).
 */
public final class PriceInfo
{
	public final int id;
	public final Long high;        // instant-buy price
	public final Long low;         // instant-sell price
	public final Long profit;      // per item, after tax, buying at low and selling at high
	public final Integer roiBp;    // ROI in basis points (1% = 100)
	public final Integer limit;    // GE buy limit per 4h
	public final Integer consistency; // % of recent hours the margin was profitable after tax
	public final Long volume24h;

	PriceInfo(int id, Long high, Long low, Long profit, Integer roiBp, Integer limit, Integer consistency, Long volume24h)
	{
		this.id = id;
		this.high = high;
		this.low = low;
		this.profit = profit;
		this.roiBp = roiBp;
		this.limit = limit;
		this.consistency = consistency;
		this.volume24h = volume24h;
	}

	public Long margin()
	{
		return high != null && low != null ? high - low : null;
	}
}
