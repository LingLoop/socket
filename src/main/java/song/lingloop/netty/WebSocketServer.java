package song.lingloop.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

@Component
@RequiredArgsConstructor
public class WebSocketServer {

    private final WebSocketServerInitializer webSocketServerInitializer;

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(webSocketServerInitializer)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture bindFuture = bootstrap.bind(8083).syncUninterruptibly();
            System.out.println("🚀 Server started on port " + 8083);

            // 여기서 블로킹 (메인 스레드가 종료되지 않게)
            bindFuture.channel().closeFuture().syncUninterruptibly();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("🧹 Shutting down Netty event loops...");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
