package com.okccc.j2se;

/**
 * @Author: okccc
 * @Date: 2025-03-18 16:38:32
 * @Desc:
 *
 * 算法复杂度
 * 基本操作(赋值/加减乘除)：只有常数项,O(1)
 * 顺序结构(代码移到下一行)：时间复杂度按加法计算
 * 循环结构(for/while)：时间复杂度按乘法计算
 * 分支结构(if)：时间复杂度取最大值
 * 时间消耗：O(1) < O(logn) < O(n) < O(nlogn) < O(n^2) < O(n^3) < O(2^n) < O(n!) < O(n^n)
 * 大O记法：处理规模为n的数据所用时间T(n)即是算法复杂度,一般只看最高次项,比如3n^2和10n^2是一个级别,复杂度默认指最坏复杂度
 * 程序 = 算法 + 数据结构,算法是解决问题的设计思想,数据结构描述了数据元素之间的关系,是算法处理问题的载体
 */
public class AlgorithmDemo {
    public static void main(String[] args) {
        // 如果a+b+c=N且a^2+b^2=c^2(a,b,c为自然数),如何求出所有a,b,c可能的组合?
        long begin = System.currentTimeMillis();
        for (int a = 0; a < 1000; a++) {
            for (int b = 0; b < 1000; b++) {
                // 当前算法复杂度：T(n) = n * n * n * (max(0,1)) = n^3 = O(n^3)
//                for (int c = 0; c < 1000; c++) {
//                    if ((a + b + c == 1000) && (a * a + b * b == c * c)) {
//                        System.out.println(a + "," + b + "," + c);
//                    }
//                }
                // 当前算法复杂度：T(n) = n * n * (1 + max(0,1)) = 2n^2 = O(n^2)
                int c = 1000 - a - b;
                if (a * a + b * b == c * c) {
                    System.out.println(a + "," + b + "," + c);
                }
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("本次运算耗时：" + (end - begin) + "ms");
    }
}
