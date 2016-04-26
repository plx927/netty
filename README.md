# Netty Project

Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.

## Links

* [Web Site](http://netty.io/)
* [Downloads](http://netty.io/downloads.html)
* [Documentation](http://netty.io/wiki/)
* [@netty_project](https://twitter.com/netty_project)

## How to build

For the detailed information about building and developing Netty, please visit [the developer guide](http://netty.io/wiki/developer-guide.html).  This page only gives very basic information.

You require the following to build Netty:

* Latest stable [Oracle JDK 7](http://www.oracle.com/technetwork/java/)
* Latest stable [Apache Maven](http://maven.apache.org/)
* If you are on Linux, you need [additional development packages](http://netty.io/wiki/native-transports.html) installed on your system, because you'll build the native transport.

Note that this is build-time requirement.  JDK 5 (for 3.x) or 6 (for 4.0+) is enough to run your Netty-based application.

## Branches to look

Development of all versions takes place in each branch whose name is identical to `<majorVersion>.<minorVersion>`.  For example, the development of 3.9 and 4.0 resides in [the branch '3.9'](https://github.com/netty/netty/tree/3.9) and [the branch '4.0'](https://github.com/netty/netty/tree/4.0) respectively.


### Channel如何向EventLoopGroup(EventLoop)中进行注册的?
查看SingleThreadEventLoop源码可以看到，将Channel向EventLoop中进行注册，其实本质是EventLoop封装了Channel向Selector注册的过程。 

```
    @Override
    public ChannelFuture register(final Channel channel, final ChannelPromise promise) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (promise == null) {
            throw new NullPointerException("promise");
        }
        //这里除了注册，还对Channel的中EventLoop进行赋值
        channel.unsafe().register(this, promise);
        return promise;
    }
    
    
  
          /**
           * AbstractUnsafe.register方法。
           *
           * 这里的注册没有像自己写的SelectableChannle.register(Selector)如此简单，
           * 而是将其作为一个任务来投放到SingleThreadEventLoop来进行处理
           */
            if (eventLoop.inEventLoop()) {
                register0(promise);
            } else {
                try {
                    //这里的Task会被添加到任务队列中来处理
                    eventLoop.execute(new OneTimeTask() {
                        @Override
                        public void run() {
                            /**
                             * 具体的注册任务执行逻辑
                             */
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    ....
                }
            }
        }
        
        
        /**
         * 具体分析Netty是如何对JDK中的Channle进行注册处理
         * @param promise
         */
        private void register0(ChannelPromise promise) {
            try {
                // check if the channel is still open as it could be closed in the mean time when the register
                // call was outside of the eventLoop
                if (!promise.setUncancellable() || !ensureOpen(promise)) {
                    return;
                }
                boolean firstRegistration = neverRegistered;

                /**
                 * ServerSocketChannel具体的处理
                 *
                 */
                doRegister();
                neverRegistered = false;
                registered = true;

                if (firstRegistration) {
                    // We are now registered to the EventLoop. It's time to call the callbacks for the ChannelHandlers,
                    // that were added before the registration was done.
                    pipeline.callHandlerAddedForAllHandlers();
                }

                safeSetSuccess(promise);
                pipeline.fireChannelRegistered();
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                if (isActive()) {
                    if (firstRegistration) {
                        pipeline.fireChannelActive();
                    } else if (config().isAutoRead()) {
                        // This channel was registered before and autoRead() is set. This means we need to begin read
                        // again so that we process inbound data.
                        //
                        // See https://github.com/netty/netty/issues/4805
                        beginRead();
                    }
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }        
        
```

画图描述出执行流程:






分析了一个Channel如何在EventLoop中进行注册的过程，下面分析一个连接如何被Netty所Accept。



对比Netty与Cobar之间的线程模型: