package map;

public class ParamterDemo {
    public static void main(String[] args) {
        /**
         * 函数的可变参数:其实就是一个数组,接收的是数组中的元素,简化书写,且可变参数的类型必须定义在参数列表的末尾
         */
        int result = add(12,23,34);
        System.out.println("result="+result);
    }
    
    public static int add(int a,int... arr){
        int  sum = 0;
        sum += a;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }
}
