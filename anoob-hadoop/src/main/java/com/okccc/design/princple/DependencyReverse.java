package com.okccc.design.princple;

/**
 * @Author: okccc
 * @Date: 2021/8/3 15:24:15
 * @Desc: 依赖倒转原则
 */
public class DependencyReverse {
    public static void main(String[] args) {
        Person p = new Person();
        p.receive(new Email());
        p.receive(new WeChat());
        p.receive(new Message());
    }

    public interface Receiver {
        String getInfo();
    }

    public static class Email implements Receiver {
        @Override
        public String getInfo() {
            return "email";
        }
    }

    public static class WeChat implements Receiver {
        @Override
        public String getInfo() {
            return "wechat";
        }
    }

    public static class Message implements Receiver {
        @Override
        public String getInfo() {
            return "message";
        }
    }

    // Person类有接收消息的方法,但是除了邮件之外还能接收短信和微信消息,所以不能写死,可以通过接口实现
    public static class Person {
        public void receive(Email email) {
            System.out.println(email.getInfo());
        }

        public void receive(Receiver receiver) {
            System.out.println(receiver.getInfo());
        }
    }
}
