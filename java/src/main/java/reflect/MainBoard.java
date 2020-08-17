package reflect;

public class MainBoard {
    
    // 主板自带功能
    public void run(){
        System.out.println("mainboard running");
    }
    
    // 主板对外提供接口
    public void usePci(PCI p){
        if(p != null){
            p.open();
            p.close();
        }
    }
}
