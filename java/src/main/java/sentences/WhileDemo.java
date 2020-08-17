package sentences;

// 循环结构
public class WhileDemo {

    public static void main(String[] args) {
        // 求1~100和        ----------------(累加思想)
        int x =1;
        int sum = 0;
        while(x<=100){
            sum += x;
            x++;
        }
        System.out.println("sum="+sum);
        
        // 求1~100之间6的倍数出现的次数     ------------------------(计数器思想)
        int y =1;
        int count = 0;
        while(y<=100){
            if(y%6 == 0){
                count++;
                // System.out.println("y="+y);
            }
            y++;
        }
        System.out.println("count="+count);
    }

}
