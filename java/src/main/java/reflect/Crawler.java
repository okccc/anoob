package reflect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
    public static void main(String[] args) throws IOException {
        /**
         * 爬虫程序:爬取网页符合一定规则的数据
         */
        
        // 封装url
        URL url = new URL("http:// www.jiazhao123.com/");
        
        // 获取输入流
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        
        // 创建list集合存储抓取的数据
        List<String> list = new ArrayList<String>();
        
        // 封装正则
        String regex = "\\d{3}-\\d{8}|\\d{3}xxxx\\d{4}";
        Pattern p = Pattern.compile(regex);
        
        // 读取数据
        String line = null;
        while((line=br.readLine()) != null){
            // 匹配器匹配读取的行
            Matcher m = p.matcher(line);
            
            // 将符合条件的数据添加到集合
            while(m.find()){
                list.add(m.group());
            }
        }
        
        // 遍历集合
        for (String s : list) {
            System.out.println(s);
        }
       
    }
}
