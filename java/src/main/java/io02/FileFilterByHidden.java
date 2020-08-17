package io02;

import java.io.File;
import java.io.FileFilter;

//  文件过滤器
public class FileFilterByHidden implements FileFilter {

    @Override
    public boolean accept(File pathname) {
        //  返回非隐藏文件
        return pathname.isHidden();
    }

}
