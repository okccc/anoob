package sentences;

public class ForDemo {
    /**
     * 当某些代码要执行很多次时,需要使用循环结构
     * 对一个条件判断一次用if,判断多次用for/while
     */
    

    public static void main(String[] args) {
        
        /*for(初始化表达式;循环条件表达式;循环后的操作表达式){
                               执行语句(循环体);
                         注意：中间的循环条件表达式一定要有,不然死循环,前后两个条件可以不写也可以写任何东西;
        }*/
        
        /*int x = 1;
        for(System.out.println("haha");x<10;System.out.println("hehe")){
            System.out.println("hello");
            x++;
        }*/
        
        // for和while区别在于变量的作用域不一样
        int x = 1;
        while(x<5){
            System.out.println("hello");
            x++;
        }
        System.out.println("x="+x);
        
        for(int y=1;y<5;y++){
            System.out.println("hello");
        }
        // System.out.println("y="+y);
        
    }

}
