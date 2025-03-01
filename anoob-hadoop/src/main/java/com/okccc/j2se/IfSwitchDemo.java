package com.okccc.j2se;

import java.util.Scanner;

/**
 * @Author: okccc
 * @Date: 2025-02-24 10:22:47
 * @Desc: debug发现if-else会依次检查条件找到匹配分支,复杂度O(n),switch可以跳表直接执行匹配分支,复杂度O(1)
 */
public class IfSwitchDemo {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.println("请输入星期：");
        int weekday = input.nextInt();

        // if-else语句(支持复杂条件匹配)
        if (weekday == 1) {
            System.out.println("Monday");
        } else if (weekday == 2) {
            System.out.println("Tuesday");
        } else if (weekday == 3) {
            System.out.println("Wednesday");
        } else if (weekday == 4) {
            System.out.println("Thursday");
        } else if (weekday == 5) {
            System.out.println("Friday");
        } else if (weekday == 6) {
            System.out.println("Saturday");
        } else if (weekday == 7) {
            System.out.println("Sunday");
        } else {
            System.out.println("输入值有误");
        }

        // switch语句(只支持常量值匹配)
        switch (weekday) {
            case 1:
                System.out.println("Monday");
                break;
            case 2:
                System.out.println("Tuesday");
                break;
            case 3:
                System.out.println("Wednesday");
                break;
            case 4:
                System.out.println("Thursday");
                break;
            case 5:
                System.out.println("Friday");
                break;
            case 6:
                System.out.println("Saturday");
                break;
            case 7:
                System.out.println("Sunday");
                break;
            default:
                System.out.println("输入值有误");
                break;
        }

        // switch表达式
        System.out.println("请输入月份：");
        int month = input.nextInt();
        switch (month) {
            case 3,4,5 -> System.out.println("Spring");
            case 6,7,8 -> System.out.println("Summer");
            case 9,10,11 -> System.out.println("Autumn");
            case 12,1,2 -> System.out.println("Winter");
            default -> throw new RuntimeException("输入值有误");
        }

        String monthName = switch (month) {
            case 3,4,5 -> "Spring";
            case 6,7,8 -> "Summer";
            case 9,10,11 -> "Autumn";
            case 12,1,2 -> "Winter";
            default -> throw new RuntimeException("输入值有误");
        };
        System.out.println("monthName = " + monthName);
    }
}
