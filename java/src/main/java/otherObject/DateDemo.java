package otherObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateDemo {
    public static void main(String[] args) throws ParseException {
        /**
         * Date:getTime()转换成毫秒值
         * DateFormat:格式化和解析日期,format()和parse()方法
         * SimpleDateFormat:(较常用)
         */
        
        method01();
        method02();
        method03();
        
    }

    /**
     * 1、Date<--->毫秒值
     */
    public static void method01() {
        // 当前系统时间毫秒值
        long l = System.currentTimeMillis();
        System.out.println(l);
        // 当前日期
        Date d = new Date();
        System.out.println(d);
        
        // 毫秒值--->日期:可以通过Date对象操作年月日等字段
        Date date = new Date(l);
        System.out.println(date);
        
        // 日期--->毫秒值:可以对具体毫秒值做加减运算
        long time = d.getTime();
        System.out.println(time);
    }
    
    /**
     * 2、Date--->String
     * DateFormat类的format()方法
     */
    public static void method02() {
        // 获取当前日期
        Date date = new Date();
        // 获取日期格式器(默认格式化风格)
        DateFormat dateFormat = DateFormat.getDateInstance();
        // 获取日期格式器(指定格式化风格)
        DateFormat dateFormat2 = DateFormat.getDateInstance(DateFormat.FULL);
        DateFormat dateFormat3 = DateFormat.getDateInstance(DateFormat.LONG);
        // 获取日期/时间格式器(默认格式化风格)
        DateFormat dateFormat4 = DateFormat.getDateTimeInstance();
        // 获取日期/时间格式器(指定格式化风格)
        DateFormat dateFormat5 = DateFormat.getDateTimeInstance(DateFormat.FULL,DateFormat.FULL);
        DateFormat dateFormat6 = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.LONG);
        
        // 自定义风格(这个常用)
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
        SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat simpleDateFormat3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // 对日期格式化
        String date1 = dateFormat.format(date);
        String date2 = dateFormat2.format(date);
        String date3 = dateFormat3.format(date);
        String date4 = dateFormat4.format(date);
        String date5 = dateFormat5.format(date);
        String date6 = dateFormat6.format(date);
        String date7 = simpleDateFormat.format(date);
        String date8 = simpleDateFormat2.format(date);
        String date9 = simpleDateFormat3.format(date);
        // 输出结果
        System.out.println("date1:"+date1);
        System.out.println("date2:"+date2);
        System.out.println("date3:"+date3);
        System.out.println("date4:"+date4);
        System.out.println("date5:"+date5);
        System.out.println("date6:"+date6);
        System.out.println("date7:"+date7);
        System.out.println("date8:"+date8);
        System.out.println("date9:"+date9);
    }

    /**
     * 3、String--->Date
     * DateFormat类的parse()方法
     * @throws ParseException
     */
    public static void method03() throws ParseException {
        // 定义一个时间字符串
        String str_date = "2017年7月11日";
        String str_date2 = "2017--7--11";
        // 获取指定风格的日期格式化器
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy--MM--dd");
        // 解析日期字符串
        Date date = dateFormat.parse(str_date);
        Date date2 = simpleDateFormat.parse(str_date2);
        // 输出结果
        System.out.println(date);
        System.out.println(date2);
    }
}
