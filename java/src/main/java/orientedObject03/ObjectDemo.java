package orientedObject03;

// Object对象的基本方法
public class ObjectDemo {
    public static void main(String[] args) {
        /* 
         * Object是所有类的根类,具备所有对象都有的共性内容
         */
        Hehe h = new Hehe();
        Student s1 = new Student(15);
        Student s2 = new Student(15);
        
        System.out.println("---------------equals方法----------------");
        // equals方法
        System.out.println(s1==s2);
        System.out.println(s1.equals(s2));
        // System.out.println(s1.equals(h));
        
        System.out.println("---------------hashCode----------------");
        // hashCode方法
        System.out.println(h.hashCode());
        System.out.println(s1.hashCode());
        System.out.println(Integer.toHexString(s1.hashCode()));// 将hash值转换成16进制
        
        System.out.println("---------------getClass方法--------------");
        // getClass方法
        System.out.println(h.getClass());
        System.out.println(h.getClass().getName());
        System.out.println(h.getClass().isArray());
        System.out.println(h.getClass().getClassLoader());
        System.out.println(s1.getClass() == s2.getClass());
        
        System.out.println("---------------toString方法--------------");
        // toString方法
        System.out.println(h);
        System.out.println(s1);
        System.out.println(h.toString());
        System.out.println(h.getClass().getName()+"@"+Integer.toHexString(h.hashCode()));
    }
}

class Student extends Object{
    private int age;
    
    Student(int age){
        this.age = age;
    }
    
    // Student类重写Object类的equals方法
    public boolean equals(Object obj){
        // 注意：此时参数是Object对象,相当于将Student向上转型了,obj是访问不到age变量的,要先向下转型
        if(obj instanceof Student){
            Student s = (Student) obj;
            return this.age == s.age;
        }else{
            throw new ClassCastException("类型转换异常");
        }
        
    }
    
    // Student类重写 Object类的hashCode方法
    public int hashCode(){
        return age;
    }
}

class Hehe{
    // 
}