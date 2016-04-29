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


#### SingleThreadEventExecutor对任务的处理
```
    /**
         * Netty中的操作都以一个任务的形式进入到EventExecutor中来执行
         * 这部分代码需要认真调式
         * @param task
         */
        @Override
        public void execute(Runnable task) {
            if (task == null) {
                throw new NullPointerException("task");
            }
    
            boolean inEventLoop = inEventLoop();
            if (inEventLoop) {
                addTask(task);
            } else {
                //启动线程，在线程中执行EventLoop的
                startThread();
                //将任务添加到阻塞队列中
                addTask(task);
                if (isShutdown() && removeTask(task)) {
                    reject();
                }
            }
    
            //默认在添加完一个任务之后会添加一个空任务
            if (!addTaskWakesUp && wakesUpForTask(task)) {
                wakeup(inEventLoop);
            }
        }

```

#### 分析ServerSocketChannel在Selector注册成功后，后续的两个操作的处理。
画图描述出执行流程:






#### 分析一个Channel如何在EventLoop中进行注册的过程，下面分析一个连接如何被Netty所Accept。







### ChannelPipeline
ChannelPipeline是存储ChannelHandler的列表集合，它可以处理和拦截一个Channel的输入事件和输出操作。ChannelPipelie实现了一个更加先进**Intercepting Filter**模式，
它可以让用户完全控制如何处理一个事件以及在pipeline中ChannelHandler如何进行互相交互。

#### ChannelPipeline的创建
每一个Channel都维护着单独的一个ChannelPipeline,并且在创建Channel的时候，ChannelPipeline会有Netty来帮助我们自动创建。
这里我们直接看`AbstractChannel`,可以看到其在构造Channel的时候，同时创建ChannelPipeline。
```
    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        unsafe = newUnsafe();
        pipeline = new DefaultChannelPipeline(this);
    }
```

通过ChannelPipeline也可以获取到它所附属的Channel。
```
    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     * @return the channel. {@code null} if this pipeline is not attached yet.
     *
     * 返回当前Pipeline的Channel。
     */
    Channel channel();  
      
      
      /**
       * Returns the {@link List} of the handler names.
       * 通过Pipeline可以获取所有的ChannelHandler的名字
       */
      List<String> names();
    
    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     * 将ChannelPipeline装换成一个有序的Map,key为ChannelHandler的名字，值为具体的ChannelHandler
     */
    Map<String, ChannelHandler> toMap();  
```
通过`names()`和`toMap()`方法可以用于测试出当前ChannelPipeline到底存储了哪些ChannelHandler。

##### 分析DefaultChannelPipeline对ChannelHandler的添加处理
```
    /**
     * Should be called before {@link #fireChannelRegistered()} is called the first time.
     */
    void callHandlerAddedForAllHandlers() {
        // This should only called from within the EventLoop.
        assert channel.eventLoop().inEventLoop();

        /**
         * 一个特殊的Runnable任务
         */
        final PendingHandlerCallback pendingHandlerCallbackHead;
        synchronized (this) {
            assert !registered;

            // This Channel itself was registered.
            registered = true;

            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            // Null out so it can be GC'ed.
            this.pendingHandlerCallbackHead = null;
        }

        // This must happen outside of the synchronized(...) block as otherwise handlerAdded(...) may be called while
        // holding the lock and so produce a deadlock if handlerAdded(...) will try to add another handler from outside
        // the EventLoop.

        //分析如果在同步代码块中产生死锁的原因。
        PendingHandlerCallback task = pendingHandlerCallbackHead;
        while (task != null) {
            task.execute();
            task = task.next;
        }
    }

```






#### IO事件如何在ChannelPipeine中处理流程    
在分析一个IO事件如何在ChannelPipeline中进行流动传输之前，我们需要先来看一看ChannelHandlerContext,因为ChannelPipeline、Channel、ChannelHandler、ChannelHandlerContext，这几个接口很容易让人犯晕，而他们之间的关系
又是密切相关的，因此在这里先对ChannelHandlerContext进行分析，对于后面分析IO事件在ChannelPipeline中应该会有更好的理解。

#### ChannelHandlerContext
```
    AbstractChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor, String name,
                                  boolean inbound, boolean outbound) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.pipeline = pipeline;
        this.name = name;
        this.executor = executor;
        this.inbound = inbound;
        this.outbound = outbound;
    }
    
    final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {
    
        private final ChannelHandler handler;
    
        DefaultChannelHandlerContext(
                DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
            super(pipeline, executor, name, isInbound(handler), isOutbound(handler));
            if (handler == null) {
                throw new NullPointerException("handler");
            }
            this.handler = handler;
        }

```
通过ChannelHandlerContext的构造方法可以看到，每一个ChannelHandlerContext中都维护着一个ChannelPipeline，同时还维护着一个Eventloop，并且ChannelHandlerContext还维护着一个ChannelHandler。

分析ChannelHandlerContext如何进行事件传播,Netty对于ChannleHandlerContext的命名说实话真的不是很好，很容易让人以为ChannelHandlerContext是全局唯一的一个东西，
实际上ChannelHandlerContext是ChannelPipeline是维护的`过滤链`的节点，在DefaultChannelPipeline中，底层就维护了`过滤链`的头部和尾部,而每个添加到ChannelPipeline中的ChannelHandler，它们
都是通过ChannelHandlerContext来进行一层包装，在进行IO事件传播的时候，通过`ChannelHandlerContext`的`fireXXX`方法来进行事件传播，下面选择Channel事件注册成功它是如何进行传播的。
```
        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            //找到当前的ChannelHandlerContext的一个节点
            final AbstractChannelHandlerContext next = findContextInbound();
            
            //获取当前ChannelHandlerContext中的EventLoop.
            //判断当前的执行线程与EventLoop中的线程是否是同一个
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                next.invokeChannelRegistered();
            } else {
                executor.execute(new OneTimeTask() {
                    @Override
                    public void run() {
                        next.invokeChannelRegistered();
                    }
                });
            }
            return this;
        }
        
        

```

#### ChannelHandler

#### ChannelInitializer
ChannelInitalizer是一个特殊的ChannelInboundHandler，它提供了一个简单的方式来当Channel在EventLoop中注册成功，就对进行初始化一个Channel，。
实现通常被用在`Bootstrap#handler(ChannelHandler)`中或者`ServerBootstrap#handler(ChannelHandler)`或者`ServerBootstrap#childHandler(ChannelHandler)`来完成对一个Channel的Pipeline的创建。
注意到`ChannelInitalizer`使用@Sharable注解来进行标注，因此我们自己在定义ChannelInitalizer必须是线程安全并且可重用的。
通过源码更深层次的理解为什么说当Channel在EventLoop中注册完成后，来完成对Channel的初始化。
```
    //当Channel在EventLoop中执行成功后(注册的过程其实本质上就是在EventLoop.thread中所执行的)
    public final void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        //通过ChannelContext来获取当前注册的Channel
        initChannel((C) ctx.channel());
        //一旦注册成功，ChannelInitlizer会从ChannelPipeline中所移除
        ctx.pipeline().remove(this);
        //触发Channel的注册事件
        ctx.fireChannelRegistered();
    }
    
```


#### ChannelFuture






































#### Netty4.0.0.Final中缓冲区内存泄漏问题

```
    PooledByteBufAllocator allocator = new PooledByteBufAllocator(true);
    //通过池化的缓冲区进行内容directBuffer的创建
    ByteBuf ioBuffer = allocator.ioBuffer(1024);

```
##### 获取一个DirectBuffer过程分析

1. 从ThreadLocal中获取一个PoolThreadCache,每一个PoolThreadCache中维护分别维护两个东西
```
    final PoolArena<byte[]> heapArena;
    final PoolArena<ByteBuffer> directArena;

```

2. 从ThreadPoolCache中获取directArena，根据directArena进行缓冲区的分配。

```
   PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        allocate(cache, buf, reqCapacity);
        return buf;
    }
```

3. 根据DirectArena来根据指定大小创建缓冲区

```
    protected PooledByteBuf<ByteBuffer> newByteBuf(int maxCapacity) {
        if (HAS_UNSAFE) {
            return PooledUnsafeDirectByteBuf.newInstance(maxCapacity);
        } else {
            return PooledDirectByteBuf.newInstance(maxCapacity);
        }
    }

```

```
 
    static PooledUnsafeDirectByteBuf newInstance(int maxCapacity) {
        PooledUnsafeDirectByteBuf buf = RECYCLER.get();
        buf.maxCapacity(maxCapacity);
        return buf;
    }

```

`Recycle`get的具体实现:

```
 public final T get() {
        Stack<T> stack = threadLocal.get();
        T o = stack.pop();
        if (o == null) {
            o = newObject(stack);
        }
        return o;
    }

```


```
    @SuppressWarnings("unchecked")
    private void recycle() {
        Recycler.Handle recyclerHandle = this.recyclerHandle;
        if (recyclerHandle != null) {
            setRefCnt(1);
            ((Recycler<Object>) recycler()).recycle(this, recyclerHandle);
        }
    }

```





第一步找到write流程里在何处buf.release的
第二步分析为何release了还泄漏

将缓冲区通过ChannelOutboundBuffer转换成MessageList

```
 void addMessage(Object msg, ChannelPromise promise) {
        int tail = this.tail;
        MessageList msgs = messages[tail];
        if (msgs == null) {
            messages[tail] = msgs = MessageList.newInstance();
        }

        msgs.add(msg, promise);

        int size = channel.calculateMessageSize(msg);
        messageListSizes[tail] += size;
        incrementPendingOutboundBytes(size);
    }

```



















对比Netty与Cobar之间的线程模型: