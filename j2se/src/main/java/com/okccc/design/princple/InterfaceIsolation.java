package com.okccc.design.princple;

/**
 * @Author: okccc
 * @Date: 2021/8/3 下午3:30
 * @Desc: 接口隔离原则
 */
public class InterfaceIsolation {
    public static void main(String[] args) {

    }

    // 接口里如果包含很多方法,这样实现该接口的子类需要重写所有方法,造成代码冗余
    public interface Inter {
        void method01();
        void method02();
        void method03();
    }

    // 将接口进行隔离,相似功能的方法放一起,实现该接口的子类只需要重写用到的方法即可
    public interface Inter01 {
        void method01();
    }

    public interface Inter02 {
        void method02();
        void method03();
    }

    public static class A implements Inter01 {
        @Override
        public void method01() {}
    }

    public static class B implements Inter01, Inter02 {
        @Override
        public void method01() {}

        @Override
        public void method02() {}

        @Override
        public void method03() {}
    }
}

