package orientedObject03;

public class InnerDemo03 {
    public static void main(String[] args) {
        /* 
         * 匿名内部类结合多态使用
         */
        new Outter().method();
    }
}

class Outter{
    void method(){
        // 创建子类对象
        new Person(){
            public void show(){
                System.out.println("haha");
            }
        }.show();
        
        // 父类引用指向子类对象(多态)
        Object obj = new Person(){
            public void show(){
                System.out.println("hehe");
            }
        };
        // 此时匿名内部类这个子类对象已经向上转型成Object类型,Object是无法访问子类特有功能的,它本身也没有show方法,所以编译不通过
//         obj.show();
        ((Person) obj).show();
    }
}
