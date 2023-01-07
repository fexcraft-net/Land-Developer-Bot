package net.fexcraft.app.ldbot;

import java.awt.Color;
import java.io.File;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommandInteraction;

import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonHandler.PrintOption;
import net.fexcraft.app.json.JsonMap;

public class LandDevBot {
	
	private static JsonMap CONFIG;
	private static DiscordApi API;
	
	public static void main(String[] args){
		log("Starting...");
		File cfgfile = new File("./config.json");
		if(!cfgfile.exists()){
			log("Config file not found, generating placeholder and exiting.");
			CONFIG = new JsonMap();
			CONFIG.add("token", "put your bot token here");
			JsonHandler.print(cfgfile, CONFIG, PrintOption.SPACED);
			System.exit(0);
		}
		CONFIG = JsonHandler.parse(cfgfile);
		if(!CONFIG.has("token") || CONFIG.get("token").string_value().equals("put your bot token here")){
			log("TOKEN CONFIG MISSING/INVALID");
			System.exit(0);
		}
		if(!CONFIG.has("channels")) CONFIG.add("channels", new JsonMap());
		log("Loaded Config.");
		API = new DiscordApiBuilder().setToken(CONFIG.get("token").string_value()).addIntents(Intent.MESSAGE_CONTENT).login().join();
		log("Joined.");
		
		API.addSlashCommandCreateListener(event -> {
			SlashCommandInteraction ico = event.getSlashCommandInteraction();
			String chid = ico.getChannel().get().getIdAsString();
			if(ico.getCommandName().equals("ping")) ico.createImmediateResponder().setContent("Pong!").setFlags(MessageFlag.EPHEMERAL).respond();
			else if(ico.getCommandName().equals("link")){
				if(!ico.getChannel().get().canManageMessages(ico.getUser())) return;
				JsonMap chs = CONFIG.getMap("channels");
				if(!chs.has(chid)) chs.add(chid, new JsonMap());
				chs = chs.getMap(chid);
				String ip = ico.getOptionByName("ip").isPresent() ? ico.getOptionByName("ip").get().getStringValue().get() : null;
				long port = ico.getOptionByName("port").isPresent() ? ico.getOptionByName("port").get().getLongValue().get() : -1;
				String ch = ico.getOptionByName("channel").isPresent() ? ico.getOptionByName("channel").get().getStringValue().get() : null;
				if(ip == null && !chs.has("ip")){
					ico.createImmediateResponder().setContent("Please set a server IP/Adress.").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(port < 0 && !chs.has("port")){
					ico.createImmediateResponder().setContent("Please set a port number.").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(ch == null && !chs.has("channel")){
					ico.createImmediateResponder().setContent("Please specify a LD message channel, e.g. `all`").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(ip != null) chs.add("ip", ip);
				if(port > 0) chs.add("port", port);
				if(ch != null) chs.add("channel", ch);
				saveConfig();
				ico.createImmediateResponder().setContent("Changes applied, use `/status` to view.").setFlags(MessageFlag.EPHEMERAL).respond();
			}
			else if(ico.getCommandName().equals("status")){
				JsonMap chs = CONFIG.getMap("channels");
				if(!chs.has(chid)) chs.add(chid, new JsonMap());
				chs = chs.getMap(chid);
				ico.createImmediateResponder().addEmbed(
					new EmbedBuilder()
						.addField("IP/Adress", chs.getString("ip", "..."))
						.addField("Port", chs.getString("port", "..."))
						.addField("Channel", chs.getString("channel", "..."))
						.setColor(Color.CYAN)
				).setFlags(MessageFlag.EPHEMERAL).respond();
			}
		});

		//SlashCommand.with("ping", "A simple ping pong command!").createGlobal(API).join();
		/*SlashCommand.with("link", "Used to link up a channel with a LD server.",
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.STRING)
				.setDescription("The Server's IP/Adress")
				.setName("ip")
				.setType(SlashCommandOptionType.STRING),
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.STRING)
				.setDescription("The port LD is listening to")
				.setName("port")
				.setType(SlashCommandOptionType.LONG),
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.STRING)
				.setDescription("The LD Channel to listen to.")
				.setName("channel")
				.setType(SlashCommandOptionType.STRING)
		).createGlobal(API).join();*/
		//SlashCommand.with("status", "Shows the link status of this channel.").createGlobal(API).join();
	}

	private static void log(Object obj){
		System.out.print(obj.toString() + "\n");
	}
	
	public static void saveConfig(){
		JsonHandler.print(new File("./config.json"), CONFIG, PrintOption.SPACED);
	}

}
