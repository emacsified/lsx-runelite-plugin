package ge.lsx;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with this plugin loaded: `gradlew run`. */
public class LsxPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LsxPlugin.class);
		RuneLite.main(args);
	}
}
