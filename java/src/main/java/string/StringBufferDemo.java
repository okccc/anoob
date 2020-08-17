package string;

public class StringBufferDemo {
    public static void main(String[] args) {
        /**
         * 三大容器:数组、集合、字符串缓冲区
         * 相互间转换方式:
         * 数组or集合--->字符串:StringBuilder+遍历
         * 字符串--->数组:split
         * 
         * StringBuffer:字符串缓冲区,用于存储数据的容器
         *          特点:1、长度可变
         *              2、可以存储不同数据类型
         *              3、最终要转成字符串使用
         */
        
        StringBuffer sb = new StringBuffer();
        // 1、添加
        sb.append("haha");
        sb.append(3);
        sb.append(true);
        System.out.println(sb);
        // 2、插入
        System.out.println(sb.insert(1, "hehe"));
        // 3、删除(包头不包尾)
        System.out.println(sb.deleteCharAt(3));
        System.out.println(sb.delete(1, 3));
        // 清空缓冲区
        // System.out.println(sb.delete(0, sb.length()));
        // 4、查找
        System.out.println(sb.indexOf("ha"));
        System.out.println(sb.charAt(3));
        // 5、修改(包头不包尾)
        System.out.println(sb.replace(2, 5, "nba"));
        sb.setCharAt(4, 'q');
        System.out.println(sb);
        // 6、反转
        System.out.println(sb.reverse());
        // StringBuffer默认初始容量16个字符,可以根据实际需要设置长度,省得长度不够另外开辟空间
        sb.setLength(20);
        System.out.println(sb.length());
    }
}
