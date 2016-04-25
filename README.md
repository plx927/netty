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


## 个人源码笔记

### 2016-04-21

#### ServerBootstrap是如何完成对Channel的初始化、注册、端口绑定操作的。



#### Channel

每一个Channel都拥有其自己的ChannelPipeline,当一个Channel被创建出来之后，ChannelPipeline也会被自动创建;而每一个ChannelPipeline中，它也会关联着当前的这个Channel。
`在AbstractNioChannel中的代码如下:,可以看到，在创建Channel时就完成了ChannelPipeline的创建,并且将Channel设置到ChannelPipeLine中`
```
   /**
     * Creates a new instance.
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        //创建ChannelPipeline,并且会将Channel设置到其所对应的Pipeline中
        pipeline = new DefaultChannelPipeline(this);
    }
```
可以看一下NioServerSocketChannel创建的过程，很简单。其实就是创建一个ServerSocketChannel，然后设置非阻塞,在创建Channel的时候，同时会自动创建Pipeline。

从Channel中可以获取到它所注册的EventLoop，而这个EventLoop是在Channel注册的时候设置的。
参考AbstractChannel.AbstractUnsafe.register(EventLoop,ChannelPromise)方法
```


```


#### EventLoop是如何对一个提交的任务进行处理的

#### EventLoop执行NIO中Selector的处理
查看run方法





#### ChannelPipeline




#### 分析一个I/O事件在ChannelPipeline的执行流程
一个I/O事件的处理流程：
在ChannelInboundHandler或者ChannelOutboundHandler所处理，在处理完成后；
通过执行定义在ChannelHandlerContext中的事件传播方法(比如有fireChannelRead或者write方法)，将事件传递给和自己最近的ChannelHandler。



#### ChannelHandlerContext
如何对I/O事件进行传播
ChannelHandlerContext的read()方法作为输出事件的传播方法：


#### ChannelInitalizer


#### ChannelHandlerInvoker




在父类中定义接口的实现，通过抽象方法让子类创建具体的实例。
newChild()
newUnsafe()


ChannelHandler的执行大体流程：

ChannelPipeline
      |
      |
ChannleHandlerContext
      |
      |
ChannelHandlerInvoker
      |
      |
ChannelHandler


#### 问题
1.EventLoop中会去判断当前执行的线程与其底层所维护的线程对象是否是同一个
除了在EventLoop底层线程未启动的情况下返回false,其他还有什么情况不是同一个？
从哪里去找到原因来证明不是同一个？
查看NioEventLoop的run方法。