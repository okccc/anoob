package orientedObject01;

// 基本数据类型在内存中的参数传递
class ObjectDemo02 {
    public static void main(String[] args) {
        int x =3;
        show(x);
        System.out.println("x="+x);  // 结果是3（画内存图比较）
    }
    
    public static void show(int a){
        a = 4;
        System.out.println("a="+a);
        // return;
    }
}


// 引用数据类型在内存中的参数传递
class Demo {
    int x = 3;
    public static void main(String[] args) {
        Demo d = new Demo();
        d.x = 9;
        show(d);
        System.out.println(d.x);  // 结果是4（画内存图比较）
    }
    
    public static void show(Demo haha){
        haha.x = 4;
        // return;
    }
}


