package net.fexcraft.app.ldbot;
//http://www.mastertheboss.com/jboss-frameworks/netty/jboss-netty-tutorial/

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonMap;

public final class NettyServer {
	
	private static ChannelFuture fut;

	public static void start(int port) throws Exception {
		EventLoopGroup main = new NioEventLoopGroup(1);
		EventLoopGroup work = new NioEventLoopGroup();
		try {
			ServerBootstrap boot = new ServerBootstrap();
			boot.group(main, work).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline p = ch.pipeline();
					p.addLast(new StringDecoder());
					p.addLast(new StringEncoder());
					p.addLast(new ServerHandler());
				}
			});
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
	public static class ServerHandler extends SimpleChannelInboundHandler<String> {
		
		private ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>();
	
		@Override
		public void channelActive(final ChannelHandlerContext ctx) {
			System.out.println("Client connected - " + ctx.channel().id());
		}
	
		@Override
		public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
			if(!msg.contains("=")) return;
			String[] split = msg.split("=");
			if(split[0].equals("token")){
				String token = ctx.channel().remoteAddress().toString() + ":" + split[1];
				if(token.startsWith("/")) token = token.substring(1);
				JsonMap map = LandDevBot.tokens();
				if(map == null){
					ctx.channel().writeAndFlush("token=invalid_Server is not linked with the bot. 0");
					ctx.close();
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
					ctx.channel().writeAndFlush("token=invalid_Server is not linked with the bot. 1");
					ctx.close();
				}
				else{
					tokens.put(ctx.channel().id().toString(), token);
					ctx.channel().writeAndFlush("OK");
					LandDevBot.log("Connection created with '" + token + "'.");
				}
				return;
			}
			if(tokens.get(ctx.channel().id().toString()) == null) return;
			if(split[0].equals("msg")){
				LandDevBot.log(msg);
				JsonMap map = JsonHandler.parse(msg.split("=")[1], true).asMap();
				ArrayList<String> chan = LandDevBot.getChannel(tokens.get(ctx.channel().id().toString()), map.get("c").string_value());
				if(chan == null){
					ctx.channel().writeAndFlush("CHANNEL_NOT_LISTED");
				}
				else{
					String mess = "**" + map.get("s").string_value() + "**: " + map.get("m").string_value();
					for(String str : chan){
						LandDevBot.api().getChannelById(str).get().asServerTextChannel().ifPresent(ch -> ch.sendMessage(mess));
					}
					ctx.channel().writeAndFlush("OK");
				}
				ctx.channel().writeAndFlush("OK");
			}
			else ctx.channel().writeAndFlush("UNKNOWN_REQUEST");
		}
		
		@Override
		public void handlerRemoved(ChannelHandlerContext ctx){
			System.out.println("Client disconnected - " + ctx.channel().id().toString());
			tokens.remove(ctx.channel().id().toString());
		}
	
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			System.out.println("Client disconnected with error - " + ctx.channel().id().toString());
			tokens.remove(ctx.channel().id().toString());
			cause.printStackTrace();
			ctx.close();
		}
		
	}

	public static void stop(){
		if(fut != null) fut.channel().close();
	}
	
}
