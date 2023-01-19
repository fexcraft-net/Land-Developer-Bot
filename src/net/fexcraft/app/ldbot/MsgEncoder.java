package net.fexcraft.app.ldbot;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MsgEncoder extends MessageToByteEncoder<Message> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
		out.writeInt(msg.length);
		if(msg.length > 0) out.writeCharSequence(msg.value, StandardCharsets.UTF_8);
	}

}
