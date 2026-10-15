// language: Java, file: TabVisualsClient.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals;

import com.loadercode.tabvisuals.gui.ClickGuiScreen;
import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.HotkeyManager;
import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.gui.hud.elements.MediaPlayer;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.media.MediaBridge;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TabVisualsClient implements ClientModInitializer {

	public static final String MOD_ID = "tabvisuals";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Window in which a tab render counts as "open" (vanilla re-renders every frame while held). */
	private static final long TAB_OPEN_GRACE_MS = 250L;

	private static volatile long lastTabRenderAt;
	private static KeyBinding clickGuiKey;
	/** Единичный флаг: повторный вызов остановки (двойная регистрация события) — no-op. */
	private static final AtomicBoolean MEDIA_STACK_STOPPED = new AtomicBoolean();

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

		// Остановка медиа-стека на выходе: без неё процесс powershell.exe моста
		// переживает JVM (спасает только PID-вотчDOG в скрипте). Дисконнект-хук
		// не ставим: мост глобальный (системный SMTC) и start() блокируется флагом
		// started — убитый при дисконнекте мост не вернулся бы после выхода из сервера.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown_media_stack());
	}

	/**
	 * Идемпотентная остановка медиа-стека на выходе из игры. MediaPlayer.shutdown()
	 * глушит control-пул плеера и убивает процесс PowerShell моста (destroyForcibly
	 * без ожидания — залипший процесс не задержит выход); MediaBridge.stop()
	 * дублируется напрямую, если HUD-элемент плеера не создавался. LrcClient и
	 * MusicManager public stop-API не имеют — их воркеры daemon-овые и JVM не удерживают.
	 */
	private static void shutdown_media_stack() {
		if (!MEDIA_STACK_STOPPED.compareAndSet(false, true)) return;
		try {
			MediaPlayer player = MediaPlayer.getInstance();
			if (player != null) {
				player.shutdown(); // controlExecutor.shutdownNow() + MediaBridge.stop()
			}
		} catch (Throwable t) {
			LOGGER.debug("media player shutdown failed", t);
		}
		try {
			MediaBridge.stop(); // идемпотентно: уже остановленный мост — no-op
		} catch (Throwable t) {
			LOGGER.debug("media bridge shutdown failed", t);
		}
	}

	/** Called from the PlayerListHud render hook, once per frame while the tab list is visible. */
	public static void onTabRender() {
		// nanoTime: монотонные отсчёты для интервала (NTP-подкрутка часов
		// wall-clock не должна ломать grace-окно)
		lastTabRenderAt = System.nanoTime();
	}

	public static boolean isTabOpen() {
		return System.nanoTime() - lastTabRenderAt < TAB_OPEN_GRACE_MS * 1_000_000L;
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
