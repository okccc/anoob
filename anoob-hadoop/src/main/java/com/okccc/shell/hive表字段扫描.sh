#!/bin/bash

# shell遍历数组两种方式
#dbs='ODS DIM DWD DWS ADS'
#for db in ${dbs}
dbs=("ODS" "DIM" "DWD" "DWS" "ADS")
for db in "${dbs[@]}"
do
    tables=$(hive -e "use ${db}; show tables")
    for table in ${tables}
    do
        # 找出所有包含手机号字段的hive表,grep -E表示正则匹配,可以写多个条件
        res=$(hive -S -e "desc ${db}.${table}" | grep -iE "phone|mobile" | wc -l)
        if [ "${res}" -gt 0 ]; then
            echo "${db}.${table}"
        fi
    done
done
