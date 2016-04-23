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



#### Channel与Pipeline的关系
Each channel has its own pipeline and it is created automatically when a new channel is created.(一一对应)


#### 分析一个I/O事件在ChannelPipeline的执行流程
一个I/O事件的处理流程：
在ChannelInboundHandler或者ChannelOutboundHandler所处理，在处理完成后；
通过执行定义在ChannelHandlerContext中的事件传播方法(比如有fireChannelRead或者write方法)，将事件传递给和自己最近的ChannelHandler。

#### ChannelHandlerContext
如何对I/O事件进行传播

ChannelHandlerContext的read()方法作为输出事件的传播方法：




