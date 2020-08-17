package generic;

public class GenericDemo03 {
    public static void main(String[] args) {
        InterImpl01 impl01 = new InterImpl01();
        impl01.show("aaa");
        
        InterImpl02<Integer> impl02 = new InterImpl02<Integer>();
        impl02.show(33);
    }   
}

//   将泛型定义在接口上
interface Inter<T>{
    public abstract void show(T t);
}

//   实现类1(指定泛型,创建该类对象时已经明确数据类型)
class InterImpl01 implements Inter<String>{

    @Override
    public void show(String t) {
        System.out.println(t);
    }
    
}

//   实现类2(不指定泛型,创建该类对象时再指定)
class InterImpl02<T> implements Inter<T>{

    @Override
    public void show(T t) {
        System.out.println(t);
    }
    
}
