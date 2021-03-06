package com.acuity.control.client.network.netty;

import com.acuity.common.network.io.FODecoder;
import com.acuity.common.network.io.FOEncoder;
import com.acuity.control.client.network.ConnectionInterface;
import com.acuity.control.client.network.response.ResponseTracker;
import com.acuity.db.domain.vertex.impl.message_package.MessagePackage;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Zach on 9/27/2017.
 */
public class NettyClient implements ConnectionInterface, SubscriberExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private ResponseTracker responseTracker = new ResponseTracker();
    private EventBus eventBus = new EventBus(this);

    private NioEventLoopGroup group = null;

    private NettyClientHandler nettyClientHandler = new NettyClientHandler(this);
    private String host;

    public void start(String host) {
        this.host = host;
        group = new NioEventLoopGroup();
        configureBootstrap(new Bootstrap(), group).connect();
    }

    public void shutdown() {
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }

    Bootstrap configureBootstrap(Bootstrap bootstrap, EventLoopGroup g) {
        bootstrap.group(g)
                .channel(NioSocketChannel.class)
                .remoteAddress(host, 2052)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(
                                new FOEncoder(),
                                new FODecoder(),
                                nettyClientHandler
                        );
                    }
                });


        return bootstrap;
    }

    public ResponseTracker getResponseTracker() {
        return responseTracker;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public void handleException(Throwable throwable, SubscriberExceptionContext subscriberExceptionContext) {
        logger.error("Error in EventBust.", throwable);
    }

    public void send(MessagePackage messagePackage) {
        nettyClientHandler.send(messagePackage);
    }

    public void disconnect() {
        nettyClientHandler.close();
    }

    public boolean isConnected() {
        return nettyClientHandler.isConnected();
    }
}
