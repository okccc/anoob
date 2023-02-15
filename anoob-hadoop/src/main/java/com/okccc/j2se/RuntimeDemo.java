package com.okccc.j2se;

/**
 * @Author: okccc
 * @Date: 2023/2/9 11:33
 * @Desc: jdk、jre、jvm
 *
 * jdk(Java Development Kit)：java开发工具包提供了编译和运行java程序所需要的各种工具和资源,包括java编译器、java运行环境、java类库
 * jre(Java Runtime Environment)：java运行环境包含jvm及其运行时用到的java类库,普通人装jvm运行代码即可,程序员要装jdk编译和调试代码
 * jvm(Java Virtual Machine)：java虚拟机负责运行字节码文件,相当于翻译官,将字节码文件翻译成当前平台认识的可执行文件,所以java是跨平台的
 * 每个操作系统都有各自的jdk,编译器通过javac命令将java代码(.java)编译成字节码文件(.class),然后由java命令运行,可以做到一次编译到处运行
 */
public class RuntimeDemo {

    public static void main(String[] args) {
        // java语言是跨平台的,因为jvm屏蔽了与具体平台相关的信息,Runtime类用来描述当前虚拟机运行状态
        // java程序启动时jvm会分配一块初始堆内存(-Xms),用完再向操作系统申请但是不能超过上限(-Xmx),不然有可能OOM
        Runtime runtime = Runtime.getRuntime();
        // jvm处理器数量
        System.out.println("processors = " + runtime.availableProcessors());

        // 因为数组索引是int类型,所以java数组最大长度2^31 - 1,如果是byte[]需要(2^31 - 1)/(1024^3)≈2G内存,int[]大概8G内存,很容易OOM
        // 有些虚拟机会占用数组开头几个长度,所以达不到Integer.MAX_VALUE,查看ArrayList源码发现MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8
        byte[] bytes = new byte[Integer.MAX_VALUE - 2];

        // maxMemory: jvm可以从操作系统申请的最大内存,默认物理机的1/4,可通过-Xmx修改
        System.out.println("maxMemory = " + runtime.maxMemory()/1024/1024 + "M");  // 3641M
        // totalMemory: jvm从操作系统申请到的内存,默认物理机的1/64,可通过-Xms修改
        // freeMemory: jvm从操作系统申请到的内存的空闲内存
        // 程序指定-Xms: 启动时先挖固定内存totalMemory,如果大部分都没用上freeMemory就会比较大,不够用再从操作系统继续挖
        // 程序未指定-Xms: 运行时从操作系统慢慢挖内存,用多少挖多少,挖的内存会有一点用不上freeMemory通常很小,调用gc方法会变大
        System.out.println("totalMemory = " + runtime.totalMemory()/1024/1024 + "M");  // 2294M
        System.out.println("freeMemory = " + runtime.freeMemory()/1024/1024 + "M");  // 225M

        // jvm新增100M内存给数组
        byte[] bytes01 = new byte[100 * 1024 * 1024];
        System.out.println("totalMemory01 = " + runtime.totalMemory()/1024/1024 + "M");  // 2294M
        System.out.println("freeMemory01 = " + runtime.freeMemory()/1024/1024 + "M");  // 125M

        // jvm新增300M内存给数组
        byte[] bytes02 = new byte[300 * 1024 * 1024];
        System.out.println("totalMemory02 = " + runtime.totalMemory()/1024/1024 + "M");  // 2594M
        System.out.println("freeMemory02 = " + runtime.freeMemory()/1024/1024 + "M");  // 125M
    }
}
