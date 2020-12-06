import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

public class TestDemo {
    @Test
    public void test01(){
        System.out.println("alala");
        System.out.println(Integer.parseInt("0"));
    }

    @Test
    public void test02(){
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
    }

}
