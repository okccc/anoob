package sentences;

// 嵌套for循环
public class ForForDemo {

    public static void main(String[] args) {
        /*
             *****
             *****
             *****
             *****
         */
        for(int x=1;x<=4;x++){                   // 外循环控制行数
            for(int y=1;y<=5;y++){               // 内循环控制每一行的列数
                System.out.print("*");
            }
            System.out.println();
        }
        
        /*
            *****
            ****
            ***
            **
            *
        */
        for(int x=1;x<=5;x++){
            for(int y=x;y<=5;y++){
                System.out.print("*");
            }
            System.out.println();
        }
        
        /*
            *
            **
            ***
            ****
            *****
        */
        for(int x=1;x<=5;x++){
            for(int y=1;y<=x;y++){
                System.out.print("*");
            }
            System.out.println();
        }
        
        /*
            54321
            5432
            543
            54
            5
        */
        for(int x=1;x<=5;x++){
            for(int y=5;y>=x;y--){
                System.out.print(y);
            }
            System.out.println();
        }
        
        /*
            1
            22
            333
            4444
            55555
        */
        for(int x=1;x<=5;x++){
            for(int y=1;y<=x;y++){
                System.out.print(x);
            }
            System.out.println();
        }
        
        /*
                    九九乘法表 (规律：每一个小单元格都是 当前列*当前行)
        1*1=1
        1*2=2 2*2=4
        1*3=3 2*3=6 3*3=9
        1*4=4 2*4=8 3*4=12 4*4=16
        ...
        */
        for(int x=1;x<=9;x++){
            for(int y=1;y<=x;y++){
                System.out.print(y+"*"+x+"="+y*x+"\t");
            }
            System.out.println();
        }
        /*关于"\"转移符的使用
        \n:换行
        \b:退格
        \t:制表符(按每个单元格划分)
        \r:按下回车键
        windows系统回车符是由\r\n两个符号组成
        linux系统回车符是\n
                    注意:"\"只对后面紧挨着的字符做转义
        */
        System.out.println("\"helloworld\"");
        
        /*
            * * * * *    * * * * *                  * * * * *
            -* * * *     * * * *     用空格往右挤                  * * * *
            --* * *      * * *      ===========>      * * *
            ---* *       * *                           * *
            ----*        *                              *
        */
        // 嵌套组合for循环
        for(int x=1;x<=5;x++){
            for(int y=1;y<x;y++){
                System.out.print(" ");
            }
            for(int z=x;z<=5;z++){
                System.out.print("* ");
            }
            System.out.println();
        }
        
    }

}
