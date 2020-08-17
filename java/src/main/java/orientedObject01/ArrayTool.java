package orientedObject01;
/**
 * 创建一个数组工具类,包含对数组的常见操作：最值,排序等
 * @author chenqian
 * @date 2017年5月5日 下午5:08:31
 */
public class ArrayTool {
    
    // 因为该类中的方法都是静态的,所以该类是不需要创建对象的,可以将构造函数私有化,这样就无法创建对象了
    private ArrayTool(){
        
    }
    
    /**
     * 打印数组功能
     */
    public static void printArray(int[] arr){
        System.out.print("{");
        for(int x=0;x<arr.length;x++){
            if(x!=arr.length-1){
                System.out.print(arr[x]+",");
            }else{
                System.out.println(arr[x]+"}");
            }
        }
    }
    
    /**
     * 求最大值
     */
    public static int getMax(int[] arr){
        int maxIndex = 0;
        for(int x=1; x<arr.length; x++){
            if(arr[x]>arr[maxIndex]){
                maxIndex = x;
            }
        }
        return arr[maxIndex];
    }
    
    /**
     * 选择排序
     */
    public static void selectSort(int[] arr){
        for(int x=0;x<arr.length-1;x++){
            // 内循环：将第一个元素依次与后面的比较大小
            for(int y=x+1;y<arr.length;y++){
                // 位置替换
                /*if(arr[x]>arr[y]){
                    int temp = arr[x];
                    arr[x] = arr[y];
                    arr[y] = temp;
                }*/
                swap(arr,x,y);
            }
        }
    }

    private static void swap(int[] arr, int a, int b) {
        if(arr[a]>arr[b]){
            int temp = arr[a];
            arr[a] = arr[b];
            arr[b] = temp;
        }
        
    }
    
    /**
     * 元素查找
     */
    public static int search(int[] arr,int key){
        for(int x=0;x<arr.length;x++){
            if(arr[x]==key){
                return x;
            }
        }
        return -1; 
    }
    
    /**
     * 将int数组转换成字符串。格式是：[e1,e2,...]
     */
    public static String arrayToString(int[] arr){
        String str = "[";

        for(int x=0; x<arr.length; x++){
            if(x!=arr.length-1){
                str = str + arr[x]+",";
            }
            else{
                str = str + arr[x]+"]";
            }
        }
        return str;
    }
    
}
