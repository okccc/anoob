package io01;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

public class LineNumberReaderDemo {

    public static void main(String[] args) throws IOException {
        /**
         * LineNumberReader:可以跟踪行号的缓冲字符输入流
         * 
         * 构造函数:
         * LineNumberReader(Reader in)
         */
        
        FileReader fr = new FileReader("E://  workspace/Java/src/io01/IO.txt");
        LineNumberReader lnr = new LineNumberReader(fr);
        
        String line = null;
        
        //  可以指定读取的行号
        lnr.setLineNumber(100);
        
        while((line = lnr.readLine()) != null){
            System.out.println(lnr.getLineNumber()+":"+line);
        }
        
        lnr.close();
    }

}
