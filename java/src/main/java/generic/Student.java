package generic;

public class Student extends Person{

    public Student() {
        super();
    }

    public Student(String name, int age) {
        super(name, age);
    }

    @Override
    public String toString() {
        return this.getName()+"....."+this.getAge();
    }
    
}
