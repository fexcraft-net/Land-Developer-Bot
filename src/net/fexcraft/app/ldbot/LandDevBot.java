package net.fexcraft.app.ldbot;

import java.io.File;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;

import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonMap;

public class LandDevBot {
	
	public static JsonMap CONFIG;
	public static DiscordApi API;
	
	public static void main(String[] args){
		CONFIG = JsonHandler.parse(new File("./config.json"));
		API = new DiscordApiBuilder().setToken(CONFIG.get("token").string_value()).addIntents(Intent.MESSAGE_CONTENT).login().join();
	}

}
