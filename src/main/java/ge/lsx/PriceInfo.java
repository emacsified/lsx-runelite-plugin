package ge.lsx;

/**
 * One item's market numbers, computed by the LSX server (profit already includes GE tax and fees).
 */
public final class PriceInfo
{
	public final int id;
	public final Long high;        // instant-buy price
	public final Long low;         // instant-sell price
	public final Long profit;      // per item, after tax and fees, buying at low and selling at high
	public final Integer roiBp;    // ROI in basis points (1% = 100)
	public final Integer limit;    // GE buy limit per 4h
	public final Integer consistency; // % of recent hours the margin was profitable after tax
	public final Long volume24h;
	public final Boolean members;
	public final String fillGrade;     // instant | quick | steady | slow | stuck
	public final Long potential4h;     // realistic profit from one 4h buy-limit window
	public final Long typicalProfit;   // median hourly profit per item over 24h, after tax and fees
	public final Long resaleFee;       // e.g. 10% to make a bought bond tradeable again
	public final boolean taxExempt;

	PriceInfo(int id, Long high, Long low, Long profit, Integer roiBp, Integer limit, Integer consistency, Long volume24h,
		Boolean members, String fillGrade, Long potential4h, Long typicalProfit, Long resaleFee, boolean taxExempt)
	{
		this.id = id;
		this.high = high;
		this.low = low;
		this.profit = profit;
		this.roiBp = roiBp;
		this.limit = limit;
		this.consistency = consistency;
		this.volume24h = volume24h;
		this.members = members;
		this.fillGrade = fillGrade;
		this.potential4h = potential4h;
		this.typicalProfit = typicalProfit;
		this.resaleFee = resaleFee;
		this.taxExempt = taxExempt;
	}

	public Long margin()
	{
		return high != null && low != null ? high - low : null;
	}

	/**
	 * A spread of a few gp leaves no room to undercut: everyone sits at the same price and the GE fills
	 * offers in queue order, so a new offer can wait a very long time.
	 */
	public boolean crowded()
	{
		Long m = margin();
		return m != null && m >= 0 && m <= 4 && volume24h != null && volume24h > 0;
	}
}
