package otherObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTest {
    public static void main(String[] args) throws ParseException {
        /**
         * 需求:计算"2017-03-07"到"2017-04-12"中间有多少天
         * 
         * 分析:只有毫秒值才可以做加减运算
         * 
         * 步骤:1、将日期由String类型转成Date类型
         *     2、将Date转成毫秒值做运算
         *     3、将毫秒值结果换算成天
         */
        
        String s1 = "2017-03-07";
        String s2 = "2017-04-12";
        method(s1, s2);
    }

    /**
     * @param s1
     * @param s2
     * @throws ParseException
     */
    public static void method(String s1, String s2) throws ParseException {
        // 获取日期格式器
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        // 解析字符串
        Date date1 = simpleDateFormat.parse(s1);
        Date date2 = simpleDateFormat.parse(s2);
        // 转成毫秒值
        long time1 = date1.getTime();
        long time2 = date2.getTime();
        long time = Math.abs(time2-time1);
        // 换算成天数
        int day = toDay(time);
        System.out.println(day);
    }

    private static int toDay(long time) {
        int day = (int) (time/1000/60/60/24);
        return day;
    }

}
