package io03;

import java.io.Serializable;

public class Person implements Serializable {
    /**
     * Serializable接口:给被序列化的类添加版本ID,判断类和对象是否同一个版本
     *        序列化作用:将内存中对象的各种状态保存下来,后面反序列化再读出来继续使用,其实就是延长生命周期
     * 
     * transient关键字:只能修饰变量,被修饰的变量无法被序列化,变量的生命周期仅存于内存而不会持久化到硬盘,一些敏感信息(密码,卡号等)通常不会被序列化
     */
    private static final long serialVersionUID = 5898267155926398171L;
    
    private transient String name;
    private int age;
    
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

}
