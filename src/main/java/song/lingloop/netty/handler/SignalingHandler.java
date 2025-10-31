package song.lingloop.netty.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import song.lingloop.netty.repository.RoomRepository;

@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class SignalingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ObjectMapper M = new ObjectMapper();
    private static final AttributeKey<String> ATTR_ROOM = AttributeKey.valueOf("room");
    private static final AttributeKey<String> ATTR_PEER = AttributeKey.valueOf("peerId");

    private final RoomRepository roomRepo;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        JsonNode msg = M.readTree(frame.text());
        String type = text(msg, "type");

        switch (type) {
            case "join" -> onJoin(ctx, msg);
            case "offer", "answer", "candidate" -> relay(ctx, msg);
            case "leave" -> onLeave(ctx);
            default -> ctx.writeAndFlush(textFrame(json("type","error","message","unknown_type")));
        }
    }

    private void onJoin(ChannelHandlerContext ctx, JsonNode msg) {
        String room = text(msg, "room");
        String peerId = text(msg, "peerId");
        if (room == null || peerId == null) {
            ctx.writeAndFlush(textFrame(json("type","error","message","room/peerId required")));
            return;
        }

        ctx.channel().attr(ATTR_ROOM).set(room);
        ctx.channel().attr(ATTR_PEER).set(peerId);
        roomRepo.join(room, peerId, ctx.channel());

        ctx.writeAndFlush(textFrame(json("type","joined","room",room,"peerId",peerId)));

        var others = roomRepo.getPeers(room).keySet().stream()
                .filter(p -> !p.equals(peerId))
                .toList();
        ctx.writeAndFlush(textFrame(json("type","peers","peers", String.join(",", others))));

        roomRepo.getPeers(room).forEach((id, ch) -> {
            if (!id.equals(peerId)) {
                ch.writeAndFlush(textFrame(json("type","peer_joined","peerId",peerId)));
            }
        });
    }
    private void relay(ChannelHandlerContext ctx, JsonNode msg) {
        String room = get(ctx, ATTR_ROOM);
        String from = get(ctx, ATTR_PEER);
        if (room == null || from == null) {
            ctx.writeAndFlush(textFrame(json("type","error","message","join first")));
            return;
        }

        var node = M.createObjectNode();
        node.put("type", text(msg,"type"))
                .put("room", room)
                .put("from", from);
        if (msg.has("sdp")) node.set("sdp", msg.get("sdp"));
        if (msg.has("candidate")) node.set("candidate", msg.get("candidate"));

        // 브로드캐스트 (본인 제외)
        roomRepo.getPeers(room).forEach((peerId, ch) -> {
            if (!peerId.equals(from)) {
                ch.writeAndFlush(new TextWebSocketFrame(node.toString()));
            }
        });
    }
//    private void relay(ChannelHandlerContext ctx, JsonNode msg) {
//
//        String room = get(ctx, ATTR_ROOM);
//        String from = get(ctx, ATTR_PEER);
//        String to = text(msg, "to");
//        System.out.printf("[relay] room=%s, from=%s, to=%s, peers=%s%n",
//                room, from, to, roomRepo.getPeers(room).keySet());
//        if (room == null || from == null || to == null) {
//            ctx.writeAndFlush(textFrame(json("type","error","message","join first / 'to' required")));
//            return;
//        }
//
//        if (!roomRepo.hasPeer(room, to)) {
//            ctx.writeAndFlush(textFrame(json("type","error","message","peer_not_found")));
//            return;
//        }
//
//        var node = M.createObjectNode();
//        node.put("type", text(msg,"type"))
//                .put("room", room)
//                .put("from", from);
//        if (msg.has("sdp")) node.set("sdp", msg.get("sdp"));
//        if (msg.has("candidate")) node.set("candidate", msg.get("candidate"));
//
//        Channel target = roomRepo.getChannel(room, to);
//        if (target != null) target.writeAndFlush(new TextWebSocketFrame(node.toString()));
//    }

    private void onLeave(ChannelHandlerContext ctx) {
        cleanup(ctx.channel());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cleanup(ctx.channel());
    }

    private void cleanup(Channel ch) {
        String room = ch.attr(ATTR_ROOM).get();
        String peer = ch.attr(ATTR_PEER).get();
        if (room != null && peer != null) {
            roomRepo.leave(room, peer);
            // 남은 사람들에게 알림
            roomRepo.getPeers(room).values().forEach(c ->
                    c.writeAndFlush(textFrame(json("type","peer_left","peerId",peer)))
            );
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static TextWebSocketFrame textFrame(String s) {
        return new TextWebSocketFrame(s);
    }

    private static String json(Object... kv) {
        try {
            var obj = M.createObjectNode();
            for (int i = 0; i < kv.length; i += 2)
                obj.put(kv[i].toString(), String.valueOf(kv[i + 1]));
            return obj.toString();
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"json_build_failed\"}";
        }
    }

    private static String get(ChannelHandlerContext ctx, AttributeKey<String> key) {
        String v = ctx.channel().attr(key).get();
        return v == null ? null : v;
    }
}
