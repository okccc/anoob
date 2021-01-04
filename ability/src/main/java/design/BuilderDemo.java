package design;

/**
 * Author: okccc
 * Date: 2020/12/31 2:09 下午
 * Desc: java构造者模式
 */
public class BuilderDemo {
    public static void main(String[] args) {
        /**
         * 构造者模式：解决多参数构造方法的初始化问题
         * 1.在类的内部创建静态内部类Builder并将其作为构造方法的参数传入
         * 2.Builder类的属性和外部类保持一致,由Builder类实现属性的setXxx方法,并最终提供build方法返回外部类对象
         *
         * 优点：通过Builder类一步一步构建复杂对象,并且可以随意组合输入参数,避免了多参数的构造方法重载出错,还不用写过多的构造器
         */
        User user = new User.Builder().setName("fly").setMobile("111").setEmail("orc@qq.com").build();
        System.out.println(user.getName());
    }
}

// 普通模式
class User01 {
    private String name;
    private String address;
    private String email;

    // 包含name和address的构造方法
    public User01(String name, String address) {
        this.name = name;
        this.address = address;
    }
    // 包含name和email的构造方法
    // 当构造方法的参数个数和参数类型完全一样时,方法重载会报错：User01(String, String) is already defined in design.User
//    public User01(String name, String email) {
//        this.name = name;
//        this.address = address;
//    }
}

// 构造者模式
class User {
    private String name;
    private String mobile;
    private String email;

    public User(Builder builder) {
        this.name = builder.name;
        this.mobile = builder.mobile;
        this.email = builder.email;
    }

    public String getName() {
        return name;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public static class Builder {
        private String name;
        private String mobile;
        private String email;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setMobile(String mobile) {
            this.mobile = mobile;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}
