package com.okccc.realtime.util;

import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: okccc
 * Date: 2022/1/13 10:22 上午
 * Desc: IK分词器工具类
 */
public class IKUtil {

    public static List<String> analyse(String text) {
        // 存放分词结果的列表
        ArrayList<String> list = new ArrayList<>();
        // 字符读取流
        StringReader reader = new StringReader(text);
        // 创建IK分词器,useSmart表示是否使用智能分词
        IKSegmenter ikSegmenter = new IKSegmenter(reader, true);
        // 遍历分词器
        try {
            Lexeme lexeme;
            while ((lexeme = ikSegmenter.next()) != null) {
                list.add(lexeme.getLexemeText());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void main(String[] args) {
        System.out.println(analyse("spark和flink区别以及各自应用场景"));
    }
}
