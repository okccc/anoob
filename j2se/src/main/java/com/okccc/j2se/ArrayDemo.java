package com.okccc.j2se;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public class ArrayDemo {
    public static void main(String[] args) {
        /*
         * 数组是引用数据类型,数组的元素可以是基本数据类型也可以是引用数据类型
         * 数组长度是固定的,一旦初始化长度不可更改,但是可以更改元素值
         * 数组中的元素是在内存空间中连续存储的
         * 不同类型数组默认初始化值：整型 0 | 浮点型 0.0 | 字符型 '\u0000' | 布尔型 false | 引用类型 null
         *
         * public：权限必须是最大的
         * static：JVM调用主函数是不需要对象的,直接用主函数所属类名调用即可
         * void：主函数没有具体返回值
         * main：jvm识别的固定名字
         * String[] args：主函数的参数列表,数组类型的参数,里面的元素都是字符串类型,args是arguments
         *
         * System.out.println(args);           // [Ljava.lang.String;@3781efb9
         * System.out.println(args.length);    // 0
         * System.out.println(args[0]);        // java.lang.ArrayIndexOutOfBoundsException: 0
         *
         * break：跳出当前循环
         * continue：跳出本次循环,继续下次循环
         * return：退出当前执行的函数,如果是main函数就退出整个程序
         * do while和while的区别在于不管条件是否满足,do while循环体至少执行一次
         */

        // 动态初始化：数组初始化和元素赋值分开进行
        int[] arr1 = new int[3];
        arr1[0] = 11;
        arr1[1] = 22;
        arr1[2] = 33;
        arr1[0] = 44;
        System.out.println(Arrays.toString(arr1));  // [44, 22, 33]

        // 静态初始化：数组初始化和元素赋值同时进行,可简写
//        int[] arr2 = new int[]{12,23,34,45,56};
        int[] arr2 = {12,23,34,45,56};

        // java中的数组是对象但是没有定义成具体的类,也就没有覆盖toString()方法的机会,println()只能调用根类Object的toString()方法
        System.out.println(arr2);  // [I@7ea987ac  @后面是十六进制的hashcode,前面[表示数组,I表示元素是int类型
        // Arrays工具类构造函数私有化,方法都是静态,不需要创建对象直接类名调用
        System.out.println(Arrays.toString(arr2));  // [12, 23, 34, 45, 56]
        // 遍历数组
        for (int i : arr2) {
            System.out.println(i);
        }
        // 数组可以转成集合
        List<int[]> list = Arrays.asList(arr2);
        System.out.println(list);

        int max = getMax(arr2);
        System.out.println(max);
        int i = search(arr2, 56);
        System.out.println(i);
        int i1 = binarySearch(arr2, 56);
        System.out.println(i1);
        int[] arr3 = {11, 33, 99, 22, 66, 44, 88, 55};
        System.out.println(Arrays.toString(arr3));
//        bubbleSort(arr3);
        selectSort(arr3);
        System.out.println(Arrays.toString(arr3));
    }

    // 二分查找：数组必须是有序的
    public static int binarySearch(int[] arr, int key) {
        int min = 0;
        int max = arr.length - 1;
        int mid;
        while (min <= max) {
            mid = (min + max) / 2;
            if (arr[mid] < key) {
                min = mid +1;
            } else if (arr[mid] > key) {
                max = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    // 查找数组元素
    public static int search(int[] arr, int key) {
        for (int i = 0; i < arr.length; i++) {
            if(arr[i] == key) {
                return i;
            }
        }
        return -1;
    }

    // 求数组最大值
    private static int getMax(int[] arr) {
        int max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
            }
        }
        return max;
    }

    // 选择排序：第一个依次与后面的比较,小的放左边,先确定最小的,如此反复
    public static void selectSort(int[] arr){
        // 外循环：遍历数组获取每一个元素,最后一个不用和自己比
        for(int x = 0; x < arr.length - 1; x++){
            // 内循环：将第一个元素依次与后面的比较大小
            for(int y = x + 1; y < arr.length; y++){
                // 位置转换
                if (arr[x] > arr[y]) {
                    int temp = arr[x];
                    arr[x] = arr[y];
                    arr[y] = temp;
                }
            }
        }
    }

    // 冒泡排序：两两之间比较,大的放右边,先确定最大的,如此反复
    public static void bubbleSort(int[] arr){
        // 外循环：遍历数组获取每一个元素
        for(int x = 0;x < arr.length; x++){
            // 内循环：参与比较大小的元素两两比较
            for(int y = 0;y < arr.length - 1 - x; y++){   //     -1防止索引越界;-x是随着外循环增加内循环参与比较的元素递减
                // 位置转换
                if(arr[y] > arr[y + 1]){
                    int temp = arr[y];
                    arr[y] = arr[y + 1];
                    arr[y + 1] = temp;
                }
            }
        }
    }

    // 斐波那契数列
    public static int fibonacci(int i) {
        if(i == 1 | i == 2) {
            return 1;
        } else {
            return fibonacci(i - 1) + fibonacci(i - 2);
        }
    }

    // 画矩形
    public static void rectangle(){
        // 外循环控制行数
        for(int x = 1; x <= 4; x++){
            // 内循环控制每行列数
            for(int y = 1; y <= 5; y++){
                System.out.print("*");
            }
            // 打印完一行换到下一行
            System.out.println();
        }
    }

    // 画正三角形
    public static void triangle01(){
        for(int x = 1; x <= 4; x++){
            for (int y = 1; y <= x; y++){
                System.out.print("*");
            }
            System.out.println();
        }
    }

    // 画倒三角形
    public static void triangle02(){
        for(int x = 1; x <= 4; x++){
            for (int y = x; y <= 4; y++){
                System.out.print("*");
            }
            System.out.println();
        }
    }

    // 打印乘法表
    public static void printCFB(){
        for(int x = 1; x <= 9; x++){
            for(int y = 1; y <= x; y++){
                System.out.print(y + "*" + x + "=" + y * x + "\t");
            }
            System.out.println();
        }
    }

}
