/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.atlasfont.providers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class ResourceProvider {

	private static final ResourceManager RESOURCE_MANAGER = MinecraftClient.getInstance().getResourceManager();
	private static final Gson GSON = new Gson();

	public static Identifier getShaderIdentifier(String name) {
		return Identifier.of("mre", "core/" + name);
	}

	public static JsonObject toJson(Identifier identifier) {
		return JsonParser.parseString(toString(identifier)).getAsJsonObject();
	}

	public static <T> T fromJsonToInstance(Identifier identifier, Class<T> clazz) {
		return GSON.fromJson(toString(identifier), clazz);
	}

	public static String toString(Identifier identifier) {
		return toString(identifier, "\n");
	}

	public static String toString(Identifier identifier, String delimiter) {
		// UTF-8 явно: ресурс лежит в джаре в своей кодировке, а без явного
		// набора InputStreamReader берёт кодовую страницу ОС — на русской
		// Windows любая кириллица в JSON ресурсов превращалась в кашу.
		try(InputStream inputStream = RESOURCE_MANAGER.open(identifier);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining(delimiter));
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}