package collection;

public class Person implements Comparable<Person>{
    private String name;
    private int age;
    
    public Person() {
        super();
    }
    
    public Person(String name, int age) {
        super();
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    //     自定义Person类自己的toString方法,返回该对象的字符串表现形式(一般都会写的简单易懂)
    public String toString() {
        return name +":"+ age;
    }

    @Override
    //     自定义Person类自己的hashCode方法
    public int hashCode() {
        System.out.println(this+"....."+"调用了Person类的hashCode方法");
        return name.hashCode() + age*99;
    }

    @Override
    //     自定义Person类自己的equals方法
    public boolean equals(Object obj) {
        System.out.println(this+"....."+"调用了Person类的equals方法");
        Person p = (Person)obj;
        return this.name.equals(p.name) && this.age==p.age;
    }
    
    public int compareTo(Person p) {
        //     按年龄排序
//           int temp = this.age - p.age;
//           return temp==0?this.name.compareTo(p.name):temp;
        //     按名字排序
        int temp = this.name.compareTo(p.name);
        return temp==0?this.age - p.age:temp;
    }
}
