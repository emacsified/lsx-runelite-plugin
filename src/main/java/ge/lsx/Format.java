package ge.lsx;

import java.text.NumberFormat;
import java.util.Locale;

final class Format
{
	private static final NumberFormat FULL = NumberFormat.getIntegerInstance(Locale.UK);

	private Format()
	{
	}

	/** Compact gp: 950, 12.5k, 1.25m, 2.1b. */
	static String gp(Long v)
	{
		if (v == null)
		{
			return "-";
		}
		long a = Math.abs(v);
		String s = v < 0 ? "-" : "";
		if (a >= 1_000_000_000L)
		{
			return s + trim(a / 1e9, 2) + "b";
		}
		if (a >= 1_000_000L)
		{
			return s + trim(a / 1e6, 2) + "m";
		}
		if (a >= 10_000L)
		{
			return s + trim(a / 1e3, 1) + "k";
		}
		return s + a;
	}

	static String full(Long v)
	{
		return v == null ? "-" : FULL.format(v);
	}

	static String pct(Integer basisPoints)
	{
		return basisPoints == null ? "-" : trim(basisPoints / 100.0, 2) + "%";
	}

	private static String trim(double v, int digits)
	{
		String s = String.format(Locale.UK, "%." + digits + "f", v);
		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s;
	}
}
