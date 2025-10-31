package song.lingloop.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import song.lingloop.netty.handler.SignalingHandler;
import song.lingloop.netty.repository.RoomRepository;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SignalingHandler signalingHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // --- 기본 HTTP 핸들러 ---
        pipeline.addLast(new HttpServerCodec());            // HTTP 디코더 + 인코더
        pipeline.addLast(new HttpObjectAggregator(65536));  // HTTP 메시지 조각 합치기
        pipeline.addLast(new ChunkedWriteHandler());        // 큰 데이터 스트림 처리

        // --- WebSocket 업그레이드 ---
        pipeline.addLast(new WebSocketServerProtocolHandler("/signal")); // 자동 핸드셰이크

        // --- 어플리케이션 레벨 핸들러 ---
        pipeline.addLast(signalingHandler);
    }
}
