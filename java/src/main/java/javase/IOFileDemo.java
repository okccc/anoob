package javase;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("all")
public class IOFileDemo {
    public static void main(String[] args) throws IOException {
        /*
         * File：文件和路径名的抽象表示
         */

//        common();
//        traverse();

//        File dir = new File("java/input/aaa");
//        recursive(dir);

//        File dir = new File("java/input");
//        File merge_file = new File("java/output/merge.txt");
//        mergeSmallFile(dir, merge_file);

//        checkList();

        splitBigFile();
    }

    public static void common() throws IOException {
        // 将文件(夹)封装成File对象
        File file = new File("java/input/ccc.txt");
        File dir = new File("java/input/aaa");
        // 获取
        System.out.println(file.getName());  // ccc.txt
        System.out.println(file.getAbsolutePath());  // D:\PycharmProjects\anoob\java\input\ccc.txt
        System.out.println(file.getPath());  // java\input\ccc.txt
        System.out.println(file.length());  // 37
        // length()只能获取文件长度,文件夹长度默认是0,应该使用dir.list().length()
        System.out.println(dir.list().length);  // 2
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        System.out.println(sdf.format(new Date(file.lastModified())));  // 2020-09-08
        // 判断
        System.out.println(file.exists());  // true
        System.out.println(file.isFile());  // true
        System.out.println(file.isDirectory());  // false
        System.out.println(file.isHidden());  // false
        // 文件的创建和删除
        if (file.exists()) {
//            file.delete();
        } else {
            file.createNewFile();
        }
        // 文件夹的创建和删除,delete()方法只能删除空文件夹
        if (dir.exists()) {
            dir.delete();
        } else {
            dir.mkdir();  // 创建单层目录
            dir.mkdirs();  // 创建多层目录
        }
    }

    public static void traverse() {
        // 将目录封装成File对象
        File dir = new File("D://config");

        // 获取文件(夹)名称,可以传入过滤器,当类很小时可以使用匿名内部类
        String[] list = dir.list();
        String[] list1 = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt");
            }
        });
        for (String s : list1) {
            System.out.println(s);
        }

        // 获取文件(夹)路径,可以传入过滤器,当类很小时可以使用匿名内部类
        File[] files = dir.listFiles();
        File[] files1 = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isHidden();
            }
        });
        for (File f : files1) {
            System.out.println(f);
        }

        // 获取系统根目录
        File[] roots = File.listRoots();
        for (File root : roots) {
            System.out.println(root);  // C:\ D:\
        }
    }

    private static void recursive(File dir) {
        // 获取该目录下所有文件(夹)
        File[] files = dir.listFiles();

        // 获取所有文件
//        for (File file : files) {
//            if(file.isFile()) {
//                System.out.println(file.getName());
//            } else {
//                System.out.println(file);
//                recursive(file);
//            }
//        }

        // 删除空文件夹
        for (File file : files) {
            if(file.isDirectory()) {
                if(file.list().length == 0) {
                    file.delete();
                } else {
                    recursive(file);
                }
            }
        }

        // 删除所有文件(夹)
//        for (File file : files) {
//            if(file.isFile()) {
//                file.delete();
//            } else {
//                if(file.list().length == 0) {
//                    file.delete();
//                } else {
//                    recursive(file);
//                }
//            }
//        }
//        dir.delete();
    }

    public static void mergeSmallFile(File dir, File merge_file) throws IOException {
        // 需求：合并小文件(字符流版本),读写数据要使用IO流
        // 方式一：深度遍历目录,将符合条件的文件逐一通过IO流写入
        File[] files = dir.listFiles();
        for (File file : files) {
            if(file.isFile()) {
                if(file.getName().endsWith(".txt")){
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    BufferedWriter bw = new BufferedWriter(new FileWriter(merge_file, true));
                    String line;
                    while ((line = br.readLine()) != null) {
                        bw.write(line);
                        bw.newLine();
                    }
                    br.close();
                    bw.close();
                }
            } else {
                mergeSmallFile(file, merge_file);
            }
        }

        // 方式二：使用序列流将多个输入流合并成一个输入流,详见IODemo.java -> sequenceStream()
    }

    public static void checkList() throws IOException {
        // 需求：列出目录下的文件清单
        File dir = new File("java/input");
        // 存放File对象的集合
        List<File> list = new ArrayList<>();
        System.out.println(list);  // []
        // 深度遍历目录将符合条件的文件添加到集合
        digui(dir, list);
        System.out.println(list);  // [java\input\aaa\bbb\ccc.txt, java\input\aaa\bbb.txt, java\input\aaa.txt]
        // 写入清单文件
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File("java/output/checklist")));
        for (File file : list) {
            bw.write(file.getAbsolutePath());
            bw.newLine();
        }
        bw.close();
    }

    private static void digui(File dir, List<File> list) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if(file.isFile()) {
                if(file.getName().endsWith(".txt")) {
                    // 将符合条件的文件添加到集合
                    list.add(file);
                }
            } else {
                digui(file, list);
            }
        }
    }

    public static void splitBigFile() throws IOException {
        // 需求：切割大文件
        File file = new File("D://aaa.avi");
        File dir = new File("D://test");
        // 输入流
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        // 输出流
        BufferedOutputStream bos = null;
        // 读写文件
        int count = 0;
        byte[] arr = new byte[1024 * 1024 * 10];
        while (bis.read(arr) != -1) {
            bos = new BufferedOutputStream(new FileOutputStream(new File(dir, getCount(++count) + ".avi")));
            bos.write(arr);
        }
        // 记录源文件及被切割的次数
        Properties prop = new Properties();
        bos = new BufferedOutputStream(new FileOutputStream(new File(dir, "readme.txt")));
        prop.setProperty("file", file.getAbsolutePath());
        prop.setProperty("count", count+"");
        prop.store(bos, "save info!");
        // 关流
        bis.close();
        bos.close();
    }

    private static String getCount(int i) {
        return i < 10 ? ("0" + i) : (i + "");
    }
}

// 后缀名过滤器
//class SuffixFilter implements FilenameFilter {
//    private final String suffix;
//
//    public SuffixFilter(String suffix) {
//        this.suffix = suffix;
//    }
//
//    @Override
//    public boolean accept(File dir, String name) {
//        return name.endsWith(suffix);
//    }
//}

// 隐藏文件过滤器
//class IsHiddenFilter implements FileFilter {
//    @Override
//    public boolean accept(File pathname) {
//        return pathname.isHidden();
//    }
//}