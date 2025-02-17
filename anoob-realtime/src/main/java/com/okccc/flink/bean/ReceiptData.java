package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: okccc
 * @Date: 2023/2/8 14:32
 * @Desc:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptData {

    public String receiptId;

    public String eventType;

    public Long timestamp;
}
