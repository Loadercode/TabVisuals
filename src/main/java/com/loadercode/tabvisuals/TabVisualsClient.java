// language: Java, file: TabVisualsClient.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals;

import com.loadercode.tabvisuals.gui.ClickGuiScreen;
import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.HotkeyManager;
import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class TabVisualsClient implements ClientModInitializer {

	public static final String MOD_ID = "tabvisuals";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Window in which a tab render counts as "open" (vanilla re-renders every frame while held). */
	private static final long TAB_OPEN_GRACE_MS = 250L;

	private static volatile long lastTabRenderAt;
	private static KeyBinding clickGuiKey;

	@Override
	public void onInitializeClient() {
		try {
			Fonts.init();
			LOGGER.info("TabVisuals initialized — render2d/render3d + font stack ready (Minecraft 1.21.4 / Fabric)");
		} catch (Throwable t) {
			LOGGER.error("TabVisuals font pre-init failed — falling back to lazy load", t);
		}

		ConfigManager.loadAutosave();

		clickGuiKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.tabvisuals.clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.tabvisuals"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (clickGuiKey.wasPressed()) {
				client.setScreen(new ClickGuiScreen());
			}
			HotkeyManager.tick(client);
		});

		HudRenderer.register();
		MusicManager.init(); // SMTC + Discord bridges start with the client
	}

	/** Called from the PlayerListHud render hook, once per frame while the tab list is visible. */
	public static void onTabRender() {
		lastTabRenderAt = System.currentTimeMillis();
	}

	public static boolean isTabOpen() {
		return System.currentTimeMillis() - lastTabRenderAt < TAB_OPEN_GRACE_MS;
	}

	/** Short label of the ClickGUI bind ("RSHIFT", "F", …) for HUD rows and the GUI hint. */
	public static String guiKeyLabel() {
		try {
			String key = clickGuiKey.getBoundKeyTranslationKey();
			if (key.startsWith("key.keyboard.")) {
				key = key.substring("key.keyboard.".length());
			} else if (key.startsWith("key.mouse.")) {
				return key.substring("key.mouse.".length()).toUpperCase(Locale.ROOT);
			} else {
				return key.toUpperCase(Locale.ROOT);
			}
			return switch (key) {
				case "right.shift" -> "RSHIFT";
				case "left.shift" -> "LSHIFT";
				case "right.control" -> "RCTRL";
				case "left.control" -> "LCTRL";
				case "right.alt" -> "RALT";
				case "left.alt" -> "LALT";
				case "space" -> "SPACE";
				case "caps.lock" -> "CAPS";
				case "tab" -> "TAB";
				case "enter" -> "ENTER";
				case "backspace" -> "BKSP";
				case "delete" -> "DEL";
				case "grave" -> "`";
				default -> key.contains(".")
						? key.substring(key.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)
						: key.toUpperCase(Locale.ROOT);
			};
		} catch (Throwable t) {
			return "RSHIFT";
		}
	}
}
