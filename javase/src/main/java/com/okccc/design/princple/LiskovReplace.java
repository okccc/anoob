package com.okccc.design.princple;

/**
 * Author: okccc
 * Date: 2021/8/3 下午5:02
 * Desc: 里式替换原则
 */
public class LiskovReplace {
    public static void main(String[] args) {
        AA aa = new AA();
        System.out.println("10+20 = " + aa.func01(10, 20));  // 10+20 = 30

        B01 b01 = new B01();
        System.out.println("10-20 = " + b01.func01(10, 20));  // 10-20 = -10

        B02 b02 = new B02();
        System.out.println("10-20 = " + b02.func01(10, 20));  // 10-20 = -10
        System.out.println("10+20 = " + b02.func02(10, 20));  // 10+20 = 30
    }

    public static class AA {
        public int func01(int a, int b) {
            return a + b;
        }
    }

    // 子类继承父类时重写方法会无意中改变父类原方法的功能
    public static class B01 extends AA {
        public int func01(int a, int b) {
            return  a - b;
        }
    }

    public static class B02 {
        public int func01(int a, int b) {
            return a - b;
        }

        // 如果要用到A01类的方法,使用组合关系
        private final AA a = new AA();
        public int func02(int a, int b) {
            return this.a.func01(a, b);
        }
    }
}

