package ge.lsx;

/**
 * Something you hold according to your LSX portfolio, with the lowest list price that gets your
 * average cost back after GE tax and fees.
 */
public final class Holding
{
	public final int itemId;
	public final long quantity;
	public final long averageCost;
	public final Long breakEven;

	Holding(int itemId, long quantity, long averageCost, Long breakEven)
	{
		this.itemId = itemId;
		this.quantity = quantity;
		this.averageCost = averageCost;
		this.breakEven = breakEven;
	}
}
