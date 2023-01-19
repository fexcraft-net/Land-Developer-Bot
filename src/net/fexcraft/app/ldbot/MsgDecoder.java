package net.fexcraft.app.ldbot;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

public class MsgDecoder extends ReplayingDecoder<Message> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Message data = new Message();
        data.length = in.readInt();
        if(data.length <= 0){
        	out.add(data);
        	return;
        }
        data.value = in.readCharSequence(data.length, StandardCharsets.UTF_8).toString();
        out.add(data);
    }
    
}
