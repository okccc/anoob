package orientedObject03;//package com.okccc.orientedObject03;
//
//import orientedObject03.Outer.Inner1;
//import orientedObject03.Outer.Inner2;
//
//
//public class InnerDemo01 {
//    public static void main(String[] args) {
//        /*
//         * 内部类:描述事物时,发现事物描述中还有事物,并且还在访问被描述的事物,这就是内部类
//         * 特点:1、内部类可以直接访问外部类的成员
//         *     2、外部类访问内部类时,必须创建内部类的对象
//         */
//
//        // 外部类访问非静态内部类要先创建对象
//        Outer.Inner1 inner = new Outer().new Inner1();
//        inner.show();
//
//        // 如果内部类是静态的,会随着外部类的加载而加载,就相当于是另一个外部类,可以直接创建对象
//        Outer.Inner2 inner2 = new Outer.Inner2();
//        inner2.speak();
//
//        // 如果内部类里有静态方法,就直接用类名调用
//        Outer.Inner2.function();
//
//        // 外部类访问局部内部类
//        Outer o = new Outer();
//        o.method();
//
//    }
//}
//
//class Outer{
//    // 外部类成员变量
//    int num = 10;
//
//    // 1、非静态内部类
//    class Inner1{
//        int num = 20;
//
//        void show(){
//            int num = 30;
//            // this指当前对象
//            System.out.println(num+".."+this.num+".."+Outer.this.num);
//        }
//
//    }
//
//    // 2、静态内部类
//    static class Inner2{
//
//        void speak(){
//            System.out.println("静态内部类的非静态方法");
//        }
//
//        // 如果内部类中定义了静态成员,那么内部类也必须是静态类,因为静态修饰的成员可以用类名直接调用,那么其所属的内部类也要是静态的,随着外部类的加载而加载
//        static void function(){
//            System.out.println("静态内部类的静态方法");
//        }
//    }
//
//    // 3、局部内部类(引出后面匿名内部类)
//    void method(){
//        final int x = 3;
//        class Inner3{
//            void show(){
//                // 局部内部类只能访问局部中被final修饰的局部变量
//                System.out.println(x);
//            }
//        }
//        new Inner3().show();
//    }
//}