package basic;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("ALL")
public class FileDemo {
    public static void main(String[] args) throws IOException {
        /**
         * File
         *
         */
        testFile();
    }

    public static void testFile() throws IOException {
        // 创建File对象,可以是文件也可以是目录
        File file = new File("java/input/aaa.txt");
        File dir = new File("java/aaa/bbb/ccc");
        // 获取
        System.out.println(file.getName());  // aaa.txt
        System.out.println(file.getAbsolutePath());  // D:\PycharmProjects\anoob\java\input\aaa.txt
        System.out.println(file.getPath());  // java\input\aaa.txt
        System.out.println(file.length());  // 37
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        System.out.println(sdf.format(new Date(file.lastModified())));  // 2020-09-08
        // 判断
        System.out.println(file.exists());  // true
        System.out.println(file.isFile());  // true
        System.out.println(file.isDirectory());  // false
        System.out.println(file.isHidden());  // false
        // 文件的创建和删除
        if (file.exists()) {
            file.delete();
        } else {
            file.createNewFile();
        }
        // 文件夹的创建和删除
        if (dir.exists()) {
            dir.delete();
        } else {
            dir.mkdir();  // 创建单层目录
            dir.mkdirs();  // 创建多层目录
        }
        // 获取系统根目录
        File[] roots = File.listRoots();
        for (File root : roots) {
            System.out.println(root);  // C:\ D:\
        }

        // 遍历文件夹
        File dir1 = new File("D://config");
        // 获取文件(目录)名称,可以传入过滤器
        String[] list = dir1.list();
        String[] list1 = dir1.list(new SuffixFilter(".txt"));
        for (String s : list) {
            System.out.println(s);
        }
        // 获取文件(目录)路径,可以传入过滤器,当类很小时可以使用匿名内部类
        File[] files = dir1.listFiles();
        File[] files1 = dir1.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isHidden();
            }
        });
        for (File f : files1) {
            System.out.println(f);
        }

    }
}

// 后缀名过滤器
class SuffixFilter implements FilenameFilter {
    private final String suffix;

    public SuffixFilter(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public boolean accept(File dir, String name) {
        return name.endsWith(suffix);
    }
}

// 隐藏文件过滤器,该类很小可以通过匿名内部类实现
class IsHiddenFilter implements FileFilter {
    @Override
    public boolean accept(File pathname) {
        return pathname.isHidden();
    }
}
