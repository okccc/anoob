import org.junit.Test;

import java.io.ObjectInputStream;

public class TestDemo {
    @Test
    public void test01(){
        System.out.println("alala");
        System.out.println(Integer.parseInt("0"));
    }

    @Test
    public void test02(){
        System.out.println("leeee");
    }

    @Test
    public void testWait() throws InterruptedException {
        new Object().wait();
    }


}
