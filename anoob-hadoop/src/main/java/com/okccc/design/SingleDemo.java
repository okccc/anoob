package com.okccc.design;

/**
 * @Author: okccc
 * @Date: 2020/12/31 2:09 下午
 * @Desc: java单例模式
 */
public class SingleDemo {
    public static void main(String[] args) {
        /*
         * 设计模式是对程序设计中普遍存在的问题提出的解决方案,核心思想就是低耦合高内聚面向接口编程
         *
         * 设计模式七大原则
         * 单一职责原则: 一个类只负责一项职责,降低类的复杂度,提高可维护性
         * 接口隔离原则: 一个类对另一个类的依赖应该建立在最小的接口上
         * 依赖倒转原则: 抽象不应该依赖细节,而是细节依赖抽象,本质上是面向接口编程
         * 里氏替换原则: 继承时尽量不要重写父类方法,继承实际上增加了两个类的耦合性,可以通过聚合/组合/依赖解决问题
         * 开闭原则OCP: 对方法开放扩展功能封闭已有功能
         * 迪米特法则: 也叫最少知道原则,即一个类对自己依赖的类知道的越少越好,只与直接朋友通信,降低耦合度
         * 合成复用原则: 尽量使用聚合/组合/依赖方式,而不使用继承
         *
         * 设计模式三种类型
         * 创建型: 单例模式、工厂模式、抽象工厂模式、构造者模式、原型模式
         * 结构型: 装饰模式、代理模式、适配器模式、桥接模式、组合模式、外观模式、享元模式
         * 行为型: 观察者模式、中介者模式、解释器模式、迭代器模式、访问者模式、备忘录模式、状态模式、策略模式、责任链模式、命令模式、模板方法模式
         *
         * 单例模式：保证类在内存中对象的唯一性
         * 1.在该类使用new创建一个本类对象
         * 2.私有化构造函数不允许其它程序创建对象
         * 3.对外提供静态get方法让其它程序可以获取该对象
         *
         * Arrays/Collections/Math/System等工具类将构造函数私有化,类中全部是静态方法,类名直接调用
         * Runtime类将构造函数私有化,对外提供getRuntime()方法获取单例对象,访问类中的非静态方法
         */

        Single s1 = Single.getInstance();
        Single s2 = Single.getInstance();
        System.out.println(s1==s2);  // 结果是true说明是同一个对象
    }

    public static class Single {
//        // 饿汉式：类一加载就创建好对象
//        public static final Single single = new Single();
//        private Single() {}
//        public static Single getInstance() {
//            return single;
//        }

        // 懒汉式：要用的时候再创建对象(延迟加载)
        public static Single single = null;
        private Single() {}
        public static Single getInstance() {
            // A进来拿到锁创建对象,B在外面一直等直到拿到锁,发现对象已经存在岂不是白等了？先判断对象是否存在,存在就直接返回,不用每次都判断锁
            if (single == null) {
                // A和B同时进来,判断为空岂不是都要创建对象？同步代码块保证线程安全,由于该方法是静态方法不能使用this,可通过反射获取对象
                synchronized (Single.class) {
                    if (single == null) {
                        single = new Single();
                    }
                }
            }
            return single;
        }
    }
}