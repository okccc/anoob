package arrays;

//     函数作用：封装代码,便于复用
public class FunctionDemo {

    public static void main(String[] args) {
        /**
         * 函数也叫方法,是定义在类中具有特定功能的一段小程序
         * 函数格式:修饰符 返回值类型 方法名(参数类型  形式参数1,参数类型  形式参数2){
         *              执行语句;
         *              return 返回值;    //     return用于结束函数,返回值返回给调用者
         *        }
         *        没有具体返回值时,返回值类型用void,return可以省略不写     
         * 函数两个明确: a、返回值类型;
         *            b、参数列表
         *        注意:返回值类型和参数类型没有直接关系
         * 注意事项:1、函数只能调用函数,不可以在函数内部定义函数     
         *        2、函数的结果应该返回给调用者,交由调用者处理
         */             
        
        //     int x = add(4,5);
        //     System.out.println("x="+x);
        
        //     draw(3,4);
        
        //     boolean res = equal(5,6);
        //     System.out.println(res);
        
        //     int max = getMax(3, 9);
        //     System.out.println("max="+max);
        
        //     print99();
        
        char res = getLevel(88);
        System.out.println("result="+res);
    }
    
    //     需求1：求和功能
    public static int add(int a,int b){
        return a+b;
    }
    
    //     需求2：画一个矩形在控制台
    public static void draw(int row,int col){
        for(int x=1;x<=row;x++){
            for(int y=1;y<=col;y++){
                System.out.print("*");
            }
            System.out.println();
        }
    }
    
    //     需求 3：比较两个数是否相等
    public static boolean equal(int a,int b){
        return a==b;
    }
    
    //     需求4：比较两个数较大的那个
    public static int getMax(int a,int b){
        return a>b?a:b;
    }
    
    //     需求5：打印99乘法表
    public static void print99(){
        for(int x=1;x<=9;x++){
            for(int y=1;y<=x;y++){
                System.out.print(y+"*"+x+"="+y*x+"\t");
            }
            System.out.println();
        }
    }
    
    //     需求6：根据考试成绩获取学生分数对应的等级
    /*90~100          A
      80~89           B
      70~79           res
      60~69           D
      60以下                           E
     */
    public static char getLevel(int num){
        char res;
        if(num>=90 && num<=100){
            res = 'A';
        }else if(num>=80 && num<=89){
            res = 'B';
        }else if(num>=70 && num<=79){
            res = 'C';
        }else if(num>=60 && num<=69){
            res = 'D';
        }else{
            res = 'E';
        }
        return res;
    }

}
