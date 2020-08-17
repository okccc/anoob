package orientedObject01;

public class ThisDemo {
    public static void main(String[] args) {
        
        Person01 p = new Person01();
        p.speak();
        Person01 p1 = new Person01("grubby");
        p1.speak();
        Person01 p2 = new Person01("moon",20);
        p2.speak();
        
        boolean b = p2.compare(p1);
        System.out.println("b="+b);
    }
    
}

class Person01{
    private String name;
    private int age;
    
    Person01(){
        name = "hehe";
        age = 18;
    }
    
    // 当本类中成员变量和局部变量重名时,可以用this关键字区分
    Person01(String name,int age){
        // this代表对象：哪个对象调用了this所在的函数,this就代表哪个对象
        this.name = name;
        this.age = age;
    }
    
    Person01(String name){
        // this也可以在构造函数中调用其他构造函数,但是只能定义在构造函数的第一行,因为初始化动作要先执行
        this();
        // this("fly",22);
        this.name = name;
    }
    
    public void speak(){
        System.out.println(this.name+":"+this.age);
    }
    
    // 判断年龄是否相同
    public boolean compare(Person01 p){
        /*if(this.age==p.age){
            return true;
        }else{
            return false;
        }*/
        return this.age==p.age;
    }
}
