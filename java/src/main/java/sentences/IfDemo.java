package sentences;
/*
流程控制语句： 1、判断结构
           2、选择结构
           3、循环结构
*/

// 判断结构
public class IfDemo {

    public static void main(String[] args) {
        // 需求1：根据用户输入的数据判断对应的星期
        /*int week =5;
        if(week == 1){
            System.out.println(week+"对应的是星期一");
        }else if(week == 2){
            System.out.println(week+"对应的是星期二");
        }else if(week == 3){
            System.out.println(week+"对应的是星期三");
        }else if(week == 4){
            System.out.println(week+"对应的是星期四");
        }else if(week == 5){
            System.out.println(week+"对应的是星期五");
        }else if(week == 6){
            System.out.println(week+"对应的是星期六");
        }else if(week == 7){
            System.out.println(week+"对应的是星期日");
        }else{
            System.out.println("该星期不存在");
        }*/
        
        // 需求二：根据用户输入的数字判断对应的季节
        int month = 5;
        if(month<1 || month>12){
            System.out.println(month+"对应的季节不存在");
        }else if(month>=3 && month<=5){
            System.out.println(month+"对应的是春季");
        }else if(month>=6 && month<=8){
            System.out.println(month+"对应的是夏季");
        }else if(month>=9 && month<=11){
            System.out.println(month+"对应的是秋季");
        }else{
            System.out.println(month+"对应的是冬季");
        }
        
    }

}
