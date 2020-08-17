package orientedObject02;

public class AbstractDemo02 {
    public static void main(String[] args) {
        /**
         * 员工案例:
         * 需求:公司中程序员有姓名,工号,薪水,工作内容
         *     项目经理有姓名,工号,薪水,工作内容和奖金
         * 对给出需求进行数据建模
         * 
         * 分析:1、程序员和项目经理存在共性内容(变量和方法),可以向上抽取成抽象类
         *     2、二者工作内容(方法)不一样,可以写成抽象方法由子类overwrite
         *     3、奖金是项目经理特有(变量),需要在子类中自定义
         *     
         */
        
        Programmer programmer = new Programmer("grubby", 001, 10000);
        programmer.work();
        Manager manager = new Manager("moon", 002, 8000, 2000);
        manager.work();
    }
}

// 描述员工
abstract class Employee{
    // 私有化成员变量,对外提供set/get方法
    private String name;
    private int id;
    private double salary;
    
    public Employee(String name, int id, double salary) {
        super();
        this.name = name;
        this.id = id;
        this.salary = salary;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public double getSalary() {
        return salary;
    }
    public void setSalary(double salary) {
        this.salary = salary;
    }
    
    // 定义抽象方法work()
    public abstract void work();
    
}

// 描述程序员
class Programmer extends Employee{

    public Programmer(String name, int id, double salary) {
        super(name, id, salary);
    }

    @Override
    public void work() {
        System.out.println("程序员干活");
    }
    


}

// 描述项目经理
class Manager extends Employee{

    // 定义项目经理特有属性奖金
    private double bonus;
    
    public double getBonus() {
        return bonus;
    }

    public void setBonus(double bonus) {
        this.bonus = bonus;
    }

    public Manager(String name, int id, double salary, double bonus) {
        super(name, id, salary);
        this.bonus = bonus;
    }

    @Override
    public void work() {
        System.out.println("项目经理装逼");
    }
    
    
}
