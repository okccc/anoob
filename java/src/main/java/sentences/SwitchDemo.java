package sentences;

// 选择结构
public class SwitchDemo {

    public static void main(String[] args) {
       /* switch(表达式){
              case 1:
                                            执行语句;
                  break;
              case 2:
                                             执行语句;
                  break;
               ...
              default:
                                            执行语句;
                  break;
        }*/
        
        // 季节案例
        int month = 2;
        switch(month){     // 四种类型：byte  short  int  char
            case 3:
            case 4:
            case 5:
                System.out.println(month+"对应的是春季 ");
                break;
            case 6:
            case 7:
            case 8:
                System.out.println(month+"对应的是夏季 ");
                break;
            case 9:
            case 10:
            case 11:
                System.out.println(month+"对应的是秋季 ");
                break;
            case 12:
            case 1:
            case 2:
                System.out.println(month+"对应的是冬季 ");
                break;
            default:
                System.out.println(month+"对应的季节不存在");
                break;
        }
        
        /*if和switch对比：
         * 1、if可以对具体值判断,也可以对区间判断(boolean)
         * 2、switch只对具体值判断且通常是固定的几个值
         * 对于几个固定数值的判断建议用switch,因为switch语句会将具体的答案都加载进内存,效率高些
         * 平常一般都用if
        */
    }
}
