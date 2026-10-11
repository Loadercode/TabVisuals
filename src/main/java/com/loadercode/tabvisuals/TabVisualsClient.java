// language: Java, file: TabVisualsClient.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals;

import com.loadercode.tabvisuals.render.font.Fonts;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TabVisualsClient implements ClientModInitializer {

	public static final String MOD_ID = "tabvisuals";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Window in which a tab render counts as "open" (vanilla re-renders every frame while held). */
	private static final long TAB_OPEN_GRACE_MS = 250L;

	private static volatile long lastTabRenderAt;

	@Override
	public void onInitializeClient() {
		try {
			Fonts.init();
			LOGGER.info("TabVisuals initialized — render2d/render3d + font stack ready (Minecraft 1.21.4 / Fabric)");
		} catch (Throwable t) {
			LOGGER.error("TabVisuals font pre-init failed — falling back to lazy load", t);
		}
	}

	/** Called from the PlayerListHud render hook, once per frame while the tab list is visible. */
	public static void onTabRender() {
		lastTabRenderAt = System.currentTimeMillis();
	}

	public static boolean isTabOpen() {
		return System.currentTimeMillis() - lastTabRenderAt < TAB_OPEN_GRACE_MS;
	}
}
