package ge.lsx;

/**
 * How much of an item's GE buy limit you've used in the current 4-hour window, worked out by LSX
 * from your logged buys. The window opens at your first buy and resets 4 hours later.
 */
public final class LimitUse
{
	public final int itemId;
	public final long used;
	public final long resetsAt; // epoch seconds

	LimitUse(int itemId, long used, long resetsAt)
	{
		this.itemId = itemId;
		this.used = used;
		this.resetsAt = resetsAt;
	}

	/** What's left of the limit, or null if the limit is unknown. Never negative. */
	Long left(Integer limit)
	{
		return limit == null ? null : Math.max(0, limit - used);
	}
}
