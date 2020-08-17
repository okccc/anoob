package generic;

public class GenericDemo02 {
    public static void main(String[] args) {
        Tool<Student> t1 = new Tool<Student>();
        Student s1 = new Student();
        Student s2 = new Student("grubby",18);
        t1.show(s1);
        t1.show(s2);
        
        Tool<String> t2 = new Tool<String>();
        t2.show("aaa");
        
        Tool<Integer> t3 = new Tool<Integer>();
        t3.show(5);
    }
}

//   将泛型定义在类上
class Tool<T>{
    private T t;

    public T getT() {
        return t;
    }

    public void setT(T t) {
        this.t = t;
    }
    
    //   将泛型定义在方法上
    public <Q> void show(Q q){
        System.out.println(q);
    }
    
}