package arrays;

//     overload:封装功能,便于复用
public class OverloadDemo {
    public static void main(String[] args) {
        /*方法重载： 1、同一个类中
                  2、函数同名
                  3、参数类型或者参数个数不同
                  4、方法重载和返回值类型无关
                  5、可以通过参数列表判断调用的是哪个同名函数(提高代码复用率)
        */     
        
        int x = add(4,5);
        int y = add(4,5,6);
        double z = add(4.5,5.6);
        System.out.println("x="+x);
        System.out.println("y="+y);
        System.out.println("z="+z);
        
        printCFB(5);
        printCFB();
    }
    
    //     求两个整数和
    public static int add(int a,int b){
        return a+b;
    }
    
    //     求两个小数和
    public static double add(double a,double b){
        return a+b;
    }
    
    //     求三个整数和
    public static int add(int a,int b,int c){
        return add(a,b)+c;
    }
    
    //     打印乘法表
    public static void printCFB(int num){
        for(int x=1;x<=num;x++){
            for(int y=1;y<=x;y++){
                System.out.print(y+"*"+x+"="+y*x+'\t');
            }
            System.out.println();
        }
    }
    
    //     打印 标准乘法表
   public static void printCFB(){
       printCFB(9);
   }

}
