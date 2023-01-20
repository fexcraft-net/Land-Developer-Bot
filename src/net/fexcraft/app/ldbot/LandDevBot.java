package net.fexcraft.app.ldbot;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Map.Entry;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommandInteraction;

import io.netty.channel.Channel;
import net.fexcraft.app.json.JsonArray;
import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonHandler.PrintOption;
import net.fexcraft.app.json.JsonMap;
import net.fexcraft.app.json.JsonObject;

public class LandDevBot {
	
	private static JsonMap CONFIG;
	private static JsonMap channels;
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
		channels = CONFIG.getMap("channels");
		if(!CONFIG.has("tokens")) CONFIG.add("tokens", new JsonMap());
		log("Loaded Config.");
		API = new DiscordApiBuilder().setToken(CONFIG.get("token").string_value()).addIntents(Intent.MESSAGE_CONTENT).login().join();
		log("Joined.");
		
		API.addSlashCommandCreateListener(event -> {
			SlashCommandInteraction ico = event.getSlashCommandInteraction();
			String chid = ico.getChannel().get().getIdAsString();
			if(ico.getCommandName().equals("ping")) ico.createImmediateResponder().setContent("Pong!").setFlags(MessageFlag.EPHEMERAL).respond();
			else if(ico.getCommandName().equals("link")){
				if(!ico.getChannel().get().canManageMessages(ico.getUser())) return;
				JsonMap chs = channels;
				boolean clear = ico.getOptionByName("clear").isPresent() ? ico.getOptionByName("clear").get().getBooleanValue().get() : false;
				if(clear){
					if(chs.has(chid)) chs.rem(chid);
					refreshTokenMap();
					saveConfig();
					ico.createImmediateResponder().setContent("Changes applied, channel is no longer linked.").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(!chs.has(chid)) chs.add(chid, new JsonMap());
				chs = chs.getMap(chid);
				String ip = ico.getOptionByName("ip").isPresent() ? ico.getOptionByName("ip").get().getStringValue().get() : null;
				String ch = ico.getOptionByName("channel").isPresent() ? ico.getOptionByName("channel").get().getStringValue().get() : null;
				String tk = ico.getOptionByName("token").isPresent() ? ico.getOptionByName("token").get().getStringValue().get() : null;
				if(ip == null && !chs.has("ip")){
					ico.createImmediateResponder().setContent("Please set a server IP/Adress.").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(ch == null && !chs.has("channel")){
					ico.createImmediateResponder().setContent("Please specify a LD message channel, e.g. `all`").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(tk == null && !chs.has("token")){
					ico.createImmediateResponder().setContent("Please specify your servers token, you can find it in the config.").setFlags(MessageFlag.EPHEMERAL).respond();
					return;
				}
				if(ip != null) chs.add("ip", ip);
				if(ch != null) chs.add("channel", ch);
				if(tk != null) chs.add("token", tk);
				refreshTokenMap();
				saveConfig();
				ico.createImmediateResponder().setContent("Changes applied, use `/status` to view.").setFlags(MessageFlag.EPHEMERAL).respond();
			}
			else if(ico.getCommandName().equals("status")){
				JsonMap chs = CONFIG.getMap("channels");
				if(!chs.has(chid)) chs.add(chid, new JsonMap());
				chs = chs.getMap(chid);
				boolean man = ico.getChannel().get().canManageMessages(ico.getUser());
				ico.createImmediateResponder().addEmbed(
					new EmbedBuilder()
						.addField("IP/Adress", chs.getString("ip", "..."))
						.addField("Channel", chs.getString("channel", "..."))
						.addField("Token", chs.has("token") ? man ? chs.get("token").string_value() : "########" : "...")
						.setColor(Color.CYAN)
				).setFlags(MessageFlag.EPHEMERAL).respond();
			}
		});
		
		API.addMessageCreateListener(event -> {
			if(event.getMessageAuthor().isBotUser()) return;
			if(!channels.has(event.getChannel().getIdAsString())) return;
			JsonMap chan = channels.getMap(event.getChannel().getIdAsString());
			Channel channel = NettyServer.ConnectionHandler.clients.get(chan.get("ip").string_value() + ":" + chan.get("token").string_value());
			if(channel == null) return;
			JsonMap map = new JsonMap();
			map.add("s", event.getMessageAuthor().getDisplayName());
			map.add("m", event.getMessageContent());
			if(event.getMessageAttachments().size() > 0){
				JsonArray attachs = new JsonArray();
				for(MessageAttachment att : event.getMessageAttachments()){
					if(att.isImage()){
						JsonArray array = new JsonArray();
						array.add(att.getUrl().toString());
						array.add(att.getWidth().get());
						array.add(att.getHeight().get());
						attachs.add(array);
					}
				}
				if(attachs.size() > 0) map.add("a", attachs);
			}
			channel.writeAndFlush(new Message("msg=" + JsonHandler.toString(map, PrintOption.FLAT)));
		});

		//SlashCommand.with("ping", "A simple ping pong command!").createGlobal(API).join();
		/*SlashCommand.with("link", "Used to link up a channel with a LD server.",
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.STRING)
				.setDescription("The Server's IP/Adress.")
				.setName("ip"),
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.STRING)
				.setDescription("The LD Channel to listen to.")
				.setName("channel"),
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.STRING)
				.setDescription("Token to validate the LD server.")
				.setName("token"),
			new SlashCommandOptionBuilder()
				.setType(SlashCommandOptionType.BOOLEAN)
				.setDescription("Used to de-link this channel.")
				.setName("clear")
		).createGlobal(API).join();*/
		//SlashCommand.with("status", "Shows the link status of this channel.").createGlobal(API).join();
		
		try{
			NettyServer.start(CONFIG.getInteger("port", 10810));
		}
		catch(Exception e){
			e.printStackTrace();
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> NettyServer.stop()));
	}

	private static void refreshTokenMap(){
		JsonMap tokens = new JsonMap();
		JsonMap map;
		for(Entry<String, JsonObject<?>> entry : channels.entries()){
			map = entry.getValue().asMap();
			if(map.has("token") && map.has("ip")){
				String tok = map.get("ip").string_value() + ":" + map.get("token").string_value();
				JsonArray chs = null;
				if(tokens.has(tok)) chs = tokens.getArray(tok);
				else{
					tokens.addArray(tok);
					chs = tokens.getArray(tok);
				}
				chs.add(entry.getKey());
			}
		}
		CONFIG.add("tokens", tokens);
	}

	public static void log(Object obj){
		System.out.println(obj == null ? "[null]" : obj.toString());
	}
	
	public static void saveConfig(){
		JsonHandler.print(new File("./config.json"), CONFIG, PrintOption.SPACED);
	}

	public static JsonMap tokens(){
		return CONFIG.has("tokens") ? CONFIG.getMap("tokens") : null;
	}

	public static ArrayList<String> getChannel(String token, String type){
		if(!CONFIG.getMap("tokens").has(token)) return null;
		JsonArray tokens = CONFIG.getMap("tokens").getArray(token);
		String cn = null, ct;
		ArrayList<String> channels = new ArrayList<>();
		for(JsonObject<?> ch : tokens.elements()){
			ch = CONFIG.getMap("channels").get(cn = ch.string_value());
			if(ch == null) continue;
			if((ct = ch.asMap().get("channel").string_value()).equals("all") || ct.equals(type)) channels.add(cn);
		}
		return channels;
	}

	public static DiscordApi api(){
		return API;
	}

}
