package com.lify.nettywebsocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.Date;

/**
 * 接收处理响应客户端websocket请求的核心业务处理类
 */
public class MyWebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;
    private static final String WEB_SOCKET_URL = "ws://127.0.0.1:8888/websocket";

    //服务端处理 客户端请求的核心方法
    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        //处理客户端向服务端发起http握手请求的业务
        if (msg instanceof FullHttpRequest) {
            handHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {//处理websocket连接业务
            handWebsocketFrame(ctx, (WebSocketFrame)msg);
        }
    }

    /**
     * 处理httpRequest 请求
     *
     * @param ctx
     * @param req
     */
    private void handHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess() || !("websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(WEB_SOCKET_URL, null, false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    /**
     * 处理 返回数据
     *
     * @param ctx
     * @param req
     * @param res
     */
    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        //服务端向客户端发送数据
        ChannelFuture future = ctx.writeAndFlush(res);
        if (res.status().code() != 200) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 处理客户端与服务端之间的websocket业务
     *
     * @param ctx
     * @param frame
     */
    private void handWebsocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {//判断关闭指令
            handshaker.close(ctx.channel(), ((CloseWebSocketFrame) frame).retain());
        }
        if (frame instanceof PingWebSocketFrame) {//判断是否为ping消息
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (!(frame instanceof TextWebSocketFrame)) {//判断是否为二进制消息
            System.out.println("不支持二进制消息");
            throw new RuntimeException("[" + this.getClass().getName() + "]不支持消息");
        }
        //返回应答
        String request = ((TextWebSocketFrame)frame).text();
        System.out.println("服务端收到客户端的消息========>>>>"+ request);
        TextWebSocketFrame tws = new TextWebSocketFrame(new Date().toString() + ctx.channel().id() + "===>>>" + request);
        //群发 消息功能
        NettyConfig.channelGroup.writeAndFlush(tws);
    }

    //工程出现异常的时候调用
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    //c s 连接的时候调用
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.channelGroup.add(ctx.channel());
        System.out.println("客户端与服务端 连接开启");
    }

    //c s断开连接的时候调用
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.channelGroup.remove(ctx.channel());
        System.out.println("客户端与服务端 连接关闭");
    }

    //服务端接收客户端发送过来的数据结束之后调用
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
