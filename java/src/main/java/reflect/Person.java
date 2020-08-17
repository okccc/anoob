package reflect;

public class Person {
    private String name;
    private int age;
    
    public Person() {
        super();
        System.out.println("这是空参构造函数");
    }

    public Person(String name, int age) {
        super();
        this.name = name;
        this.age = age;
    }
    
    public void show(){
        System.out.println(name+"..."+age);
    }
    
}
