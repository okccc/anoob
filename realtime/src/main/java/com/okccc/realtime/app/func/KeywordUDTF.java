package com.okccc.realtime.app.func;

import com.okccc.realtime.util.IKUtil;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.util.List;

/**
 * @Author: okccc
 * @Date: 2022/1/13 11:02 上午
 * @Desc: flink自定义函数
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/dev/table/functions/udfs/
 *
 * 自定义标量函数 extends org.apache.flink.table.functions.ScalarFunction：一对一(UDF)
 * 自定义表值函数 extends org.apache.flink.table.functions.TableFunction<Row>：一对多(UDTF)
 * 自定义聚合函数 extends org.apache.flink.table.functions.AggregateFunction：多对一(UDAF)
 */
// Row代表一行,指定其有多少列,分词完就是一个个单词所有只有一列
@FunctionHint(output = @DataTypeHint("ROW<word STRING>"))
public class KeywordUDTF extends TableFunction<Row> {

    // 由于传入参数不确定,所以父类并没有提供具体方法让子类继承,所以不用写@Override
    public void eval(String str) {
        // 使用IK分词器进行分词
        List<String> keywordList = IKUtil.analyse(str);
        for (String keyword : keywordList) {
            // 收集结果往下游发送
            collect(Row.of(keyword));
        }
    }

    // 可以写多个eval方法,调用者根据参数不同来区分
    public void eval(String str, int num) {
        for (String s : str.split(" ")) {
            // use collect(...) to emit a row
            collect(Row.of(s, num));
        }
    }
}
