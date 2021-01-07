#!/bin/bash

# 老集群hive导出表结构
databases='jlgl_ads jlgl_dim jlgl_dw jlgl_dm jlgl_rpt ods'
for database in ${databases}
do
    hive -e "use ${database}; show tables" > "${database}"-tables.txt
    sed -i '/^WARN/d' "${database}"-tables.txt
    echo "use ${database};" > "${database}"_tables_ddl.txt
    cat "${database}"-tables.txt | while read eachline
    do
        hive -e "use ${database}; show create table ${eachline};" >> "${database}"_tables_ddl.txt
        echo ";" >> "${database}"_tables_ddl.txt
    done
    sed -i '/WARN/d;/LOCATION/d;/hdfs/d' "${database}"_tables_ddl.txt
    sed -i 's/CREATE TABLE/CREATE TABLE IF NOT EXISTS/g' "${database}"_tables_ddl.txt
done

# 新集群hive导入表结构
hive -f "${database}"_tables_ddl.txt