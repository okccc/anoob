package arrays;

//     获取数组中的最大值
public class ArrayMaxDemo {
    
    public static void main(String[] args) {
        int[] arr = {24,12,66,35,99,78};
        int max = getMax(arr);
        int max2 = getMax2(arr);
        System.out.println("max="+max);
        System.out.println("max2="+max2);
    }
    
    public static int getMax(int[] arr){
        //     假设第一个元素是最大值
        int max = arr[0];
        //     遍历获取数组中每一个元素 
        for(int x=1;x<arr.length;x++){
            //     将后面的每个元素和定义的max比较大小,大就替换掉
            if(arr[x]>max){
                max = arr[x];
            }
        }
        return max;
    }
    
    public static int getMax2(int[] arr){
        //     假设第一个索引对应的 元素是最大值
        int max = 0;
        //     遍历循环数组获取每一个元素
        for(int x=1;x<arr.length;x++){
            //     将后面的每个元素和定义的max比较大小,大就替换掉
            if(arr[x]>arr[max]){
                max = x;
            }
        }
        return arr[max];
    }
}
