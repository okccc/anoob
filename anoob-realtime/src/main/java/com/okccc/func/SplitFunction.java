package com.okccc.func;

import com.okccc.util.IKUtil;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.io.IOException;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2023/6/16 10:49:22
 * @Desc: 分词是一对多,FlinkSql没有提供相关内置函数,需要自定义UDTF实现
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/systemfunctions/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/#overview
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/#scalar-functions
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/#table-functions
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/#aggregate-functions
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/#table-aggregate-functions
 *
 * Automatic Type Inference
 * The automatic type inference inspects the function’s class and evaluation methods to derive data types for the
 * arguments and result of a function. @DataTypeHint and @FunctionHint annotations support the automatic extraction.
 * 1.@DataTypeHint 可以作用于方法,自动提取方法的参数和返回值类型
 * 2.@FunctionHint 可以作用于类,对多个重载方法声明一次通用的结果类型
 *
 * 自定义标量函数 extends org.apache.flink.table.functions.ScalarFunction：一对一(UDF)
 * 自定义表值函数 extends org.apache.flink.table.functions.TableFunction<Row>：一对多(UDTF)
 * 自定义聚合函数 extends org.apache.flink.table.functions.AggregateFunction：多对一(UDAF)
 */
// 分词是将一行变成多行,Row代表一行,分词完就是一个个单词所有只有一列
@FunctionHint(output = @DataTypeHint("ROW<word STRING>"))
public class SplitFunction extends TableFunction<Row> {

    // For SQL queries, a function must always be registered under a name.
    // For interactive sessions, it is also possible to parameterize functions before using or registering them.
    // In this case, function instances instead of function classes can be used as temporary functions.
    // It requires that the parameters are serializable for shipping function instances to the cluster.
    // 对于交互式会话,还可以在使用或注册函数之前对其进行参数化,这样可以把函数实例而不是函数类注册为临时函数
    // 为确保函数实例可应用于集群环境,参数必须是可序列化的
    // env.createTemporarySystemFunction("SplitFunction", SplitFunction.class);
    // env.createTemporarySystemFunction("SplitFunction", new SplitFunction(true));
    private boolean endInclusive;

    public SplitFunction() {
    }

    public SplitFunction(boolean endInclusive) {
        this.endInclusive = endInclusive;
    }

    public String eval(String s, Integer begin, Integer end) {
        return s.substring(begin, endInclusive ? end + 1 : end);
    }

    // 由于传入参数不确定,所以父类并没有提供具体方法让子类继承,所以不用写@Override
    public void eval(String str) {
        // 使用IK分词器进行分词
        List<String> list;
        try {
            list = IKUtil.splitKeyword(str);
            for (String word : list) {
                // 收集数据往下游发送
                collect(Row.of(word));
            }
        } catch (IOException e) {
            // 有异常说明切词切不动了,那就把该词当做整体返回
//            e.printStackTrace();
            collect(Row.of(str));
        }
    }

    // 可以重载多个eval方法,调用者根据参数不同来区分
    public void eval(String str, int length) {
        for (String word : str.split(" ")) {
            // use collect(...) to emit a row
            collect(Row.of(word, length));
        }
    }
}
