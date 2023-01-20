package net.fexcraft.app.ldbot;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonMap;

/**
 * 
 * @author Ferdinand Calo' (FEX___96)
 *
 */
public final class NettyServer {
	
	private static ChannelFuture fut;

	public static void start(int port) throws Exception {
		EventLoopGroup main = new NioEventLoopGroup(1);
		EventLoopGroup work = new NioEventLoopGroup();
		try {
			ServerBootstrap boot = new ServerBootstrap();
			boot.group(main, work).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>(){
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new MsgDecoder(), new MsgEncoder(), new ConnectionHandler());
				}
			}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

			fut = boot.bind(port).sync();
			LandDevBot.log("Netty Server started.");
			fut.channel().closeFuture().sync();
		}
		finally{
			main.shutdownGracefully();
			work.shutdownGracefully();
		}
	}

	@Sharable
	public static class ConnectionHandler extends ChannelInboundHandlerAdapter {
		
		private static ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>();
		public static ConcurrentHashMap<String, Channel> clients = new ConcurrentHashMap<>();
	
		@Override
		public void channelActive(final ChannelHandlerContext ctx) {
			LandDevBot.log("Client connected - " + ctx.channel().id());
			LandDevBot.log("Clients connected so far: " + tokens.size());
		}
	
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
			Message msg = (Message)obj;
			if(msg.length <= 0) return;
			if(!msg.value.contains("=")) return;
			String[] split = msg.value.split("=");
			if(split[0].equals("token")){
				String token = ctx.channel().remoteAddress().toString().split(":")[0] + ":" + split[1];
				if(token.startsWith("/")) token = token.substring(1);
				JsonMap map = LandDevBot.tokens();
				if(map == null){
					ctx.channel().writeAndFlush(new Message("token=invalid (" + token + ")\nServer is not linked with the bot. 0")).addListener(ChannelFutureListener.CLOSE);
					return;
				}
				boolean found = false;
				for(String key : map.value.keySet()){
					if(key.equals(token)){
						found = true;
						break;
					}
				}
				if(!found){
					ctx.channel().writeAndFlush(new Message("token=invalid (" + token + ")\nServer is not linked with the bot. 1")).addListener(ChannelFutureListener.CLOSE);
				}
				else{
					tokens.put(ctx.channel().id().toString(), token);
					clients.put(token, ctx.channel());
					ctx.channel().writeAndFlush(new Message(0));
					LandDevBot.log("Connection created with '" + token + "'.");
				}
				return;
			}
			if(tokens.get(ctx.channel().id().toString()) == null) return;
			if(split[0].equals("msg")){
				//LandDevBot.log(msg.value);
				JsonMap map = JsonHandler.parse(split[1], true).asMap();
				ArrayList<String> chan = LandDevBot.getChannel(tokens.get(ctx.channel().id().toString()), map.get("c").string_value());
				if(chan == null){
					ctx.channel().writeAndFlush(new Message("CHANNEL_NOT_LISTED"));
				}
				else{
					String[] mesg = {  map.get("m").string_value() }; 
					if(map.has("s")) mesg[0] = "**" + map.get("s").string_value() + "**: " + mesg[0];
					for(String str : chan){
						LandDevBot.api().getChannelById(str).get().asServerTextChannel().ifPresent(ch -> ch.sendMessage(mesg[0]));
					}
					ctx.channel().writeAndFlush(new Message(0));
				}
			}
			else ctx.channel().writeAndFlush(new Message("UNKNOWN_REQUEST"));
		}
		
		@Override
		public void handlerRemoved(ChannelHandlerContext ctx){
			LandDevBot.log("Client disconnected - " + ctx.channel().id().toString());
			String token = tokens.remove(ctx.channel().id().toString());
			if(token != null) clients.remove(token);
			LandDevBot.log("Clients still connected: " + tokens.size());
		}
	
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			LandDevBot.log("Client errored - " + ctx.channel().id().toString());
			//cause.printStackTrace();
			//ctx.close();
		}
		
	}

	public static void stop(){
		if(fut != null) fut.channel().close();
	}
	
}
