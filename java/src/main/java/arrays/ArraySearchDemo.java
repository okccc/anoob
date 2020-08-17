package arrays;

//     二分查找法
public class ArraySearchDemo {

    public static void main(String[] args) {
        int[] arr = {12,23,34,45,56,77,88,99};
        //     int x = search(arr, 45);
        //     System.out.println("x="+x);
        
        int y = binarySearch(arr, 56);
        System.out.println("y="+y);
    }
    
    //     基本查找
    public static int search(int[] arr,int key){
        for(int x=0;x<arr.length;x++){
            if(arr[x]==key){
                return x;
            }
        }
        return -1; //     找不到就默认返回-1,表示不存在
    }
    
    //     二分查找(折半查找)：数组必须是有序的
    public static int binarySearch(int[] arr,int key){
        int min,mid,max;
        min = 0;
        max = arr.length-1;
        while(min<=max){
            mid = (min+max)/2;
            if(arr[mid]<key){
                min=mid+1;
            }else if(arr[mid]>key){
                max=mid-1;
            }else{
                return mid;
            }
        }
        return -1;
    }
}
