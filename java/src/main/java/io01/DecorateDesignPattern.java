package io01;

public class DecorateDesignPattern {
    public static void main(String[] args) {
        /**
         * 装饰设计模式:如果想要对已有的对象进行功能增强,可以自定义装饰类,通过构造方法接收被装饰的对象,基于对象已有功能,提供更高功能
         *       案例:BufferedReader、BufferedWriter
         * 
         * 装饰设计模式和继承区别:
         * 1、两者都可以对已有对象进行功能扩展
         * 2、如果为了实现某个功能,对体系内的所有类都添加子类,会很臃肿,为何不把功能本身单独封装呢？哪个对象需要这个功能关联进来即可
         * Writer
         *      |--FileWriter:操作文件
         *         |--BufferedFileWriter
         *      |--StringWriter:操作字符串
         *         |--BufferedStringWriter
         *      ...
         * class BufferedWriter extends Writer{
         *      BufferedWriter(Writer w){
         *      
         *      }
         * }
         * 注意:装饰类和被装饰类要属于同一个父类或接口,这样才能在已有功能上提高更高功能
         */
        
        new Person().eat();
        new NewPerson(new Person()).eat();
    }
}

//  被装饰类
class Person{
    public void eat(){
        System.out.println("来碗大米饭!");
    }
}

//  装饰类
class NewPerson{
    
    private final Person p;
    
    //  将已有对象作为装饰类的构造函数的参数传入
    NewPerson(Person p){
        this.p = p;
    }
    
    public void eat(){
        System.out.println("先来点水果！");
        p.eat();
        System.out.println("再来点甜品！");
    }

    
}
