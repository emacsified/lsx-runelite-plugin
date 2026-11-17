package ge.lsx;

public enum OverlayValue
{
	PROFIT("Flip profit (after tax)"),
	MARGIN("Margin"),
	ROI("ROI %"),
	SELL_PRICE("Instant-sell price"),
	BUY_PRICE("Instant-buy price");

	private final String label;

	OverlayValue(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
