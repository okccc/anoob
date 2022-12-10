package com.okccc.realtime.bean;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author: okccc
 * @Date: 2021/12/27 2:55 下午
 * @Desc: 商品主题实体类
 * Builder：使用构造者模式创建对象,给度量数据设置初始值后,将流数据转换成实体类时就不用再挨个赋值
 * Builder.Default：构造者模式创建对象时初始值会丢失,比如给属性赋初始值0L,不加该注解就会变成null
 */
@Data
@Builder
public class ProductStats {
    // 窗口区间(开窗)
    String stt;
    String edt;

    // 维度数据(分组,先按sku_id进行分组,剩下的通过维表关联补全)
    Long sku_id;            // sku编号
    String sku_name;        // sku名称
    BigDecimal sku_price;   // sku单价
    Long spu_id;            // spu编号
    String spu_name;        // spu名称
    Long tm_id;             // 品牌编号
    String tm_name;         // 品牌名称
    Long category3_id;      // 类别编号
    String category3_name;  // 类别名称

    // 度量数据(聚合)
    @Builder.Default
    Long click_ct = 0L;  // 点击数
    @Builder.Default
    Long display_ct = 0L;  // 曝光数
    @Builder.Default
    Long favor_ct = 0L;  // 收藏数
    @Builder.Default
    Long cart_ct = 0L;  // 添加购物车数
    @Builder.Default
    Long order_ct = 0L;  // 订单数
    @Builder.Default
    Long order_sku_num = 0L;  // 订单商品个数
    @Builder.Default
    BigDecimal order_amount = BigDecimal.ZERO;  // 订单金额
    @Builder.Default
    Long paid_order_ct = 0L;  // 支付订单数
    @Builder.Default
    BigDecimal payment_amount = BigDecimal.ZERO;  // 支付金额
    @Builder.Default
    Long refund_order_ct = 0L;  // 退款订单数
    @Builder.Default
    BigDecimal refund_amount = BigDecimal.ZERO;  // 退款金额
    @Builder.Default
    Long comment_ct = 0L;  // 评论数
    @Builder.Default
    Long good_comment_ct = 0L;  // 好评数
    @Builder.Default
    @TransientSink
    Set orderIdSet = new HashSet();  // 通过Set对订单/支付/退款去重,辅助统计字段,不写入数据库
    @Builder.Default
    @TransientSink
    Set paidOrderIdSet = new HashSet();
    @Builder.Default
    @TransientSink
    Set refundOrderIdSet = new HashSet();

    // 统计时间
    Long ts;
}
