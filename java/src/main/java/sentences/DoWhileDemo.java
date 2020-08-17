package sentences;

public class DoWhileDemo {
    public static void main(String[] args) {
          /* do{
                                       执行语句;
           }while(条件表达式);*/
        
        int x =1;
        while(x<1){
            System.out.println("x="+x);
            x++;
        }
        
        int y =1;
        do{
            System.out.println("y="+y);
            y++;
        }while(y<1);
        
        // do while和while的区别在于不管条件是否满足,do while循环体至少执行一次
    }
}
