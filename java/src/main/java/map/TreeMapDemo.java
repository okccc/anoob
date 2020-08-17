package map;//package com.okccc.map;
//
//import generic.ComparatorByName;
//import generic.Person;
//
//import java.util.Iterator;
//import java.util.Map.Entry;
//import java.util.TreeMap;
//
//public class TreeMapDemo {
//    public static void main(String[] args) {
//
//        TreeMap<Person, String> tm = new TreeMap<Person,String>(new ComparatorByName());
//        tm.put(new Person("grubby",18), "荷兰");
//        tm.put(new Person("moon",19), "韩国");
//        tm.put(new Person("fly",18), "天朝");
//        tm.put(new Person("grubby",18), "荷兰");
//        System.out.println(tm);
//
//        Iterator<Entry<Person, String>> it = tm.entrySet().iterator();
//        while(it.hasNext()){
//            Entry<Person, String> e = it.next();
//            Person key = e.getKey();
//            String value = e.getValue();
//            System.out.println(key.getName()+"....."+key.getAge()+"....."+value);
//        }
//    }
//
//}
