package sentences;

public class BreakContinueReturn {

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        /**
         * 常见语句
         * 
         * break:跳出当前循环
         * continue:跳出本次循环,继续下次循环
         * return:退出当前执行的函数,如果是main函数就退出整个程序
         */
            
        for(int x=1;x<=5;x++){
            if(x==4){
                break;
            }
            System.out.println("x="+x);
        }
        
        for(int x=1;x<=5;x++){
            for(int y=1;y<=5;y++){
                System.out.print("hehe");
                break;
            }
            System.out.println("haha");
        }
        
        aaa:for(int x=1;x<=5;x++){
            bbb:for(int y=1;y<=5;y++){
                System.out.println("hehe");
                break aaa;
            }
            System.out.println("haha");
        }
        
        System.out.println("====================================");
        
        for(int x=1;x<=5;x++){
            if(x%2==0){
                continue;
            }
            System.out.println("x="+x);
        }
        
        for(int x=1;x<=3;x++){
            if(x%2==0){
                continue;
            }
            for(int y=1;y<=3;y++){
                System.out.println("y="+y);
            }
            System.out.println("x="+x);
        }
        
    }

}
