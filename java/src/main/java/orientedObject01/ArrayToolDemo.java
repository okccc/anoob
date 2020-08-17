package orientedObject01;

public class ArrayToolDemo {
    public static void main(String[] args) {
        int[] arr = {12,23,56,33,89};
        
        // 都是静态函数,可以直接用类名调用,不需要创建对象
        int max = ArrayTool.getMax(arr);
        System.out.println("max="+max);
        
        ArrayTool.selectSort(arr);
        ArrayTool.printArray(arr);
        
        int index = ArrayTool.search(arr, 55);
        System.out.println("index="+index);
        
        String arrayToString = ArrayTool.arrayToString(arr);
        System.out.println(arrayToString);
        
    }
}
