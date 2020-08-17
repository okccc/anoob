package arrays;

//     数组排序
public class ArraySortDemo {
    
    public static void main(String[] args) {
        int[] arr = {12,33,25,44,88,56,3};
        printArray(arr);
        selectSort(arr);
        //     bubbleSort(arr);
        printArray(arr);
        
    }
    
    //     打印数组
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
    
    //     选择排序：第一个依次与后面的比较,小的放左边,先确定最小的,如此反复
    public static void selectSort(int[] arr){
        //     外循环：遍历数组获取每一个元素,最后一个不用和自己比
        for(int x=0;x<arr.length-1;x++){
            //     内循环：将第一个元素依次与后面的比较大小
            for(int y=x+1;y<arr.length;y++){
                //     位置替换
                /*if(arr[x]>arr[y]){
                    int temp = arr[x];
                    arr[x] = arr[y];
                    arr[y] = temp;
                }*/
                swap(arr,x,y);
            }
        }
    }
    
    //     冒泡排序：两两之间比较,大的放右边,先确定最大的,如此反复
    public static void bubbleSort(int[] arr){
        //     外循环：遍历数组获取每一个元素
        for(int x=0;x<arr.length;x++){
            //     内循环：参与比较大小的元素两两比较
            for(int y=0;y<arr.length-1-x;y++){   //     -1防止索引越界;-x是随着外循环增加内循环参与比较的元素递减
                //     位置替换
                /*if(arr[y]>arr[y+1]){
                    int temp = arr[y];
                    arr[y] = arr[y+1];
                    arr[y+1] = temp;
                }*/
                swap(arr,y,y+1);
            }
        }
    }
    
    //     替换位置代码封装,以便复用
    public static void swap(int[] arr,int a,int b){
        if(arr[a]>arr[b]){
            int temp = arr[a];
            arr[a] = arr[b];
            arr[b] = temp;
        }
    }
    
    
}
