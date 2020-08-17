package otherObject;

import java.util.Calendar;

public class CalendarDemo {
    public static void main(String[] args) {
        /**
         * Calendar:日历   get()、set()、add()
         * 
         * 需求:求任意一年的二月份天数,并判断最后一天是星期几
         * 分析:二月份天数是不确定的,但是最后一天肯定是3月1号的前一天
         */
        
        int year = 2017;
        showDays(year);
    }

    public static void showDays(int year) {
        // 获取日历对象
        Calendar c = Calendar.getInstance();
        // 将日历设成3月1日
        c.set(year, 2, 1);
        // 减去一天
        c.add(Calendar.DAY_OF_MONTH, -1);
        // 输出天数
        showDate(c);
    }

    /**
     * 获取日历的年月日星期
     */
    public static void showDate(Calendar c) {
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH)+1;
        int day = c.get(Calendar.DAY_OF_MONTH);
        int week = c.get(Calendar.WEEK_OF_MONTH);
        System.out.println(year+"年2月份有"+day+"天");
        System.out.println("这一天是"+year+"年"+month+"月"+day+"日  "+getWeek(week));
    }

    private static String getWeek(int week) {
        String[] weeks = {"","星期日","星期一","星期二","星期三","星期四","星期五","星期六"};
        return weeks[week];
    }
}
