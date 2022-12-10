package com.okccc.j2se;

import java.io.Serializable;

/**
 * @Author: okccc
 * @Date: 2021/11/15 下午3:45
 * @Desc: java模板类
 */
public class Person implements Serializable, Comparable<Person> {

    private static final long SERIAL_VERSION_UID = 5898267155926398171L;
    private String name;
    private int age;
    private transient String idcard;

    public Person() {
        super();
    }

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public Person(String name, int age, String idcard) {
        this.name = name;
        this.age = age;
        this.idcard = idcard;
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

    public String getIdcard() {
        return idcard;
    }

    public void setIdcard(String idcard) {
        this.idcard = idcard;
    }

    @Override
    public String toString() {
        // 重写toString方法,返回该对象的字符串表现形式(通常会写的简单易懂)
//        return name + ": " + age + ": " + idcard;
        return "Person[" + "name: " + name + ", age: " + age + ", idcard: " + idcard + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Person){
            Person p = (Person)obj;
            return this.name.equals(p.name) && this.age==p.age;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + age * 99;
    }

    @Override
    public int compareTo(Person p) {
        // 按年龄排序
//       int temp = this.age - p.age;
//       return temp==0 ? this.name.compareTo(p.name) : temp;
        // 按名字排序
        int temp = this.name.compareTo(p.name);
        return temp==0 ? this.age - p.age : temp;
    }
}