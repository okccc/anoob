package otherObject;

public class MathDemo {
    public static void main(String[] args) {
        /**
         * Math:该类没有构造函数,无法创建对象,所以都是静态方法,类名直接调用
         */
        
        // 向上取整
        System.out.println(Math.ceil(12.33));
        // 向下取整
        System.out.println(Math.floor(12.33));
        // 四舍五入
        System.out.println(Math.round(12.33));
        // a的b次方
        System.out.println(Math.pow(2, 3));
        // 绝对值
        System.out.println(Math.abs(-12));
        // 0~1随机数
        System.out.println(Math.random());
    }
}
