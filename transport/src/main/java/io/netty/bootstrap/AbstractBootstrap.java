/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.bootstrap;

import io.netty.channel.*;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {

    //这里的EventLoopGroup是Boss线程池
    volatile EventLoopGroup group;

    @SuppressWarnings("deprecation")
    private volatile ChannelFactory<? extends C> channelFactory;
    private volatile SocketAddress localAddress;
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> attrs = new LinkedHashMap<AttributeKey<?>, Object>();
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        synchronized (bootstrap.attrs) {
            attrs.putAll(bootstrap.attrs);
        }
    }

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-created
     * {@link Channel}
     *
     * 这里的Group是用于处理 “将要被创建的这个Channel” 所发生的IO事件。
     * Channel没有在这里所定义,而在这里只是定义了一个ChannelFactory。
     *
     * 这里可以参考一下ChannelFactory中对于泛型的用法。
     *
     * 返回的B可能是Bootstrap，也可能是Serverbootstrap。
     */
    @SuppressWarnings("unchecked")
    public B group(EventLoopGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return (B) this;
    }

    /**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(io.netty.channel.ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public B channel(Class<? extends C> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new ReflectiveChannelFactory<C>(channelClass));
    }

    /**
     * @deprecated Use {@link #channelFactory(io.netty.channel.ChannelFactory)} instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return (B) this;
    }

    /**
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} for
     * simplify your code.
     *
     * 注意,Netty中的ChannelFactory有两个:
     * 一个是io.netty.bootstrap包中的，另外一个是io.netty.channel包中的。
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }

    /**
     * The {@link SocketAddress} which is used to bind the local "end" to.
     */
    @SuppressWarnings("unchecked")
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return (B) this;
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     *
     * 设置Channel的选项配置,主要用于对网络传输连接的配置信息
     * 比如:
     * ChannelOption.SO_BACKLOG，ChannelOption.SO_TIMEOUT等信息
     * 当ChannelOption的值为null时，则将该ChannelOption的参数移除。
     * 因为底层使用的是非线程安全的Map,防止多个线程通过操作AbstractBootstrap引导类，因此底层对Map进行了同步操作。
     *
     */
    @SuppressWarnings("unchecked")
    public <T> B option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }

        if (value == null) {
            synchronized (options) {
                options.remove(option);
            }
        } else {
            synchronized (options) {
                options.put(option, value);
            }
        }
        return (B) this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    @SuppressWarnings("unchecked")
    public <T> B attr(AttributeKey<T> key, T value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            synchronized (attrs) {
                attrs.remove(key);
            }
        } else {
            synchronized (attrs) {
                attrs.put(key, value);
            }
        }
        return (B) this;
    }

    /**
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    @SuppressWarnings("unchecked")
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        /*
         * 这里会去检测ChannelFactory的创建，当我们在设置完成Channel之后，底层就会显示的帮助我们创建ChannelFactory。
         */
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return (B) this;
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration.  This method is useful when making
     * multiple {@link Channel}s with similar settings.  Please note that this method does not clone the
     * {@link EventLoopGroup} deeply but shallowly, making the group a shared resource.
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     * ServerBootstrap的bind方法所完成的任务:
     * 1.将ServerSocketChannel往EventLoop中进行注册
     * 2.端口的绑定
     * 具体实现为{@link AbstractBootstrap#doBind(SocketAddress)}
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        return doBind(localAddress);
    }


    /**
     * Channel对于端口异步绑定的实现处理
     * 1.先完成Channel在EventLoop中的注册
     * 2.当注册成功后，才完成端口的绑定操作
     *
     * @param localAddress 绑定的地址与端口信息
     * @return 返回ChannelFutrue,当事件处理完成之后，ChanneFutrue会得到通知。
     */
    private ChannelFuture doBind(final SocketAddress localAddress) {
        /*
         * 初始化Channel，然后对Channel实现异步的注册,通过返回ChannelFutrue来表示Channel在EventLopp中注册的结果
         *
         * 注意到:Netty是先完成Channel在EventLoop中的注册操作，只有当Channel注册成功才会完成Channel的绑定操作
         * 而将Channel向EventLoop中注册的过程又是异步处理的。
         */
        final ChannelFuture regFuture = initAndRegister();


        final Channel channel = regFuture.channel();

        /*
         * 判断是否有I/O异常发生,可以看到在initAndRegister的时候也判断了一次注册过程是否发生异常
         * 因此可以看到异步处理在获取到结果后总是反复的判断。
         */
        if (regFuture.cause() != null) {
            return regFuture;
        }

        /**
         * 由于Netty中的所有I/O操作都是异步的，因此在执行下面的代码的时候我们并不知道是否已经执行完成
         */
        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();

            //当Channel已经注册完成并且成功注册,此时完成Channel的端口绑定操作
            doBind0(regFuture, channel, localAddress, promise);

            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.

            /*
             * 注册的结果通常而言在这个时候都已经得到了，这里是针对于还未得到结果的特殊处理
             * 这里通过在之前返回的Future中添加监听器，用户获取最后注册的结果,如果执行成功,则对Channel进行真正的端口绑定。
             */
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.executor = channel.eventLoop();
                    }
                    doBind0(regFuture, channel, localAddress, promise);
                }
            });
            return promise;
        }
    }


    /**
     * 重要:
     * Channel初始化以及Channel向EventLoop中进行注册
     *
     * @return
     */
    final ChannelFuture initAndRegister() {
        //通过Channel工厂完成Channel的创建,默认使用的是ReflectiveChannelFactory
        final Channel channel = channelFactory().newChannel();
        try {
            //完成对Channel的初始化,具体实现取决于ServerBootstrap与Bootstrap的实现
            init(channel);
        } catch (Throwable t) {
            channel.unsafe().closeForcibly();
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        /**
         *  注意:这里的group是boss线程池，用于接收客户端连接
         *  NioEventLoopGroup boss = new NioEventLoopGroup();
         *  NioEventLoopGroup worker= new NioEventLoopGroup();
         *
         *  ServerBootstrap bootstrap = new ServerBootratrp();
         *  bootstrap.group(boss,worker);
         *  可以看到ServerBootstrap在创建的时候将外部传入的boos设置到了该group中。
         *
         *  具体参考{@link io.netty.bootstrap.AbstractBootstrap#group(EventLoopGroup)}说明
         *
         *  将Channel异步注册到EventLoopGroup(NioEventLoopGroup)中
         *  NioEventLoopGroup会从其所管理的NioEventLoop中选择一个执行真正的注册处理。
         *  具体参考{@link io.netty.channel.MultithreadEventLoopGroup#register(Channel)}方法。
         *
         *  因为NioEventLoopGroup继承了MultithreadEventLoopGroup
         */
        ChannelFuture regFuture = group().register(channel);

        /*
         * 对于异步执行处理,每次返回执行的回调结果之后，
         * 在执行某个操作时，总是先判断一下执行是否结果。
         */
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return regFuture;
    }

    abstract void init(Channel channel) throws Exception;


    /**
     * 真正的端口绑定操作
     *
     * 这里才是真正完成Channel端口绑定的过程,Netty是先对Channel进行注册,然后再对Channel绑定。
     * 通过获取到Boss线程池执行一个"一次性"的任务,这种{@link OneTimeTask}，绝对不能重复提交。
     *
     * @param regFuture Channel往EventLoop中注册的结果
     * @param channel
     * @param localAddress
     * @param promise
     */
    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.

        /*
         * 从上面的描述中我们可以看到,这个方法的执行是在ChannelHandler的channelRegistered方法之前执行。
         * 让用户所编写的ChannelHandler可以在channelRegistered方法中有机会去进行Pipeline的设置。
         */
        channel.eventLoop().execute(new OneTimeTask() {
            @Override
            public void run() {
                //判断Channel在EventLoop中注册是否成功,如果已经注册成功,那么对Channel进行绑定
                if (regFuture.isSuccess()) {
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    //通过promise通过用户Channel注册失败的原因
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    @SuppressWarnings("unchecked")
    public B handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
        return (B) this;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    @SuppressWarnings("deprecation")
    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    /**
     * Return the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     */
    public EventLoopGroup group() {
        return group;
    }

    final Map<ChannelOption<?>, Object> options() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return attrs;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('(');
        if (group != null) {
            buf.append("group: ")
               .append(StringUtil.simpleClassName(group))
               .append(", ");
        }
        if (channelFactory != null) {
            buf.append("channelFactory: ")
               .append(channelFactory)
               .append(", ");
        }
        if (localAddress != null) {
            buf.append("localAddress: ")
               .append(localAddress)
               .append(", ");
        }
        synchronized (options) {
            if (!options.isEmpty()) {
                buf.append("options: ")
                   .append(options)
                   .append(", ");
            }
        }
        synchronized (attrs) {
            if (!attrs.isEmpty()) {
                buf.append("attrs: ")
                   .append(attrs)
                   .append(", ");
            }
        }
        if (handler != null) {
            buf.append("handler: ")
               .append(handler)
               .append(", ");
        }
        if (buf.charAt(buf.length() - 1) == '(') {
            buf.append(')');
        } else {
            buf.setCharAt(buf.length() - 2, ')');
            buf.setLength(buf.length() - 1);
        }
        return buf.toString();
    }

    private static final class PendingRegistrationPromise extends DefaultChannelPromise {
        // Is set to the correct EventExecutor once the registration was successful. Otherwise it will
        // stay null and so the GlobalEventExecutor.INSTANCE will be used for notifications.
        private volatile EventExecutor executor;

        private PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        @Override
        protected EventExecutor executor() {
            EventExecutor executor = this.executor;
            if (executor != null) {
                // If the registration was a success executor is set.
                //
                // See https://github.com/netty/netty/issues/2586
                return executor;
            }
            // The registration failed so we can only use the GlobalEventExecutor as last resort to notify.
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
