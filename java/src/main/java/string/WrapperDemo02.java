package string;

import java.util.Arrays;



public class WrapperDemo02 {
    
    public static final String space_separator = " ";
    
    public static void main(String[] args) {
        /**
         * 需求：对字符串"20 78 9 -7 88 36 29"数值进行从小到大的排序
         * 
         * 分析:1、将字符串变成字符串数组
         *     2、将字符串数组变成int数组
         *     3、对int数组排序
         *     4、将排序后的int数组还原成字符串
         */
        String str = "20 78 9 -7 88 36 29";
        
        System.out.println(str);
        str = sort(str);
        System.out.println(str);
    }

    private static String sort(String str) {
        
        // 1、将字符串变成字符串数组
        String[] str_arr = stringToArray(str);
        
        // 2、将字符串数组变成int数组
        int[] arr = toIntArray(str_arr);
        
        // 3、对int数组排序
        Arrays.sort(arr);
        
        // 4、将int数组变成字符串
        String result = intArrayToString(arr);
        return result;
    }
    
    /**
     * @param str
     * @return
     */
    public static String[] stringToArray(String str) {
        String[] str_arr = str.split(space_separator);
        return str_arr;
    }

    /**
     * @param str_arr
     * @return
     * @throws NumberFormatException
     */
    public static int[] toIntArray(String[] str_arr) throws NumberFormatException {
        int[] arr = new int[str_arr.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = Integer.parseInt(str_arr[i]);
        }
        return arr;
    }

    public static String intArrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if(i!=arr.length-1){
                sb.append(arr[i]+" ");
            }else{
                sb.append(arr[i]);
            }
        }
        return sb.toString();
    }


    
}
