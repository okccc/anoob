### datax
```shell
# github地址：https://github.com/alibaba/DataX
# 下载
[root@cdh1 ~]$ wget http://datax-opensource.oss-cn-hangzhou.aliyuncs.com/datax.tar.gz
# 安装
[root@cdh1 ~]$ tar -xvf datax.tar.gz -C /opt/module
# 测试脚本
[root@cdh1 ~]$ bin/datax.py job/job.json
# 查看stream2stream模板
[root@cdh1 ~]$ bin/datax.py -r streamreader -w streamwriter
[root@cdh1 ~]$ bin/datax.py job/stream2stream.json
# 查看mysql2stream模板
[root@cdh1 ~]$ bin/datax.py -r mysqlreader -w streamwriter
[root@cdh1 ~]$ bin/datax.py job/mysql2stream.json
# 查看mysql2hdfs模板
[root@cdh1 ~]$ bin/datax.py -r mysqlreader -w hdfswriter
[root@cdh1 ~]$ bin/datax.py job/mysql2hdfs.json
```

### stream2stream
```json
{
    "job": {
        "content": [
            {
                "reader": {
                    "name": "streamreader",
                    "parameter": {
                        "column": [
                            {
                                "type": "string",
                                "value": "Hello DataX"
                            },
                            {
                                "type": "date",
                                "value": "2023-07-05 17:49:55"
                            }
                        ],
                        "sliceRecordCount": 10000
                    }
                },
                "writer": {
                    "name": "streamwriter",
                    "parameter": {
                        "encoding": "UTF-8",
                        "print": true
                    }
                }
            }
        ],
        "setting": {
            "speed": {
                "channel": 2
            }
        }
    }
}
```

### mysql2stream
```json
{
    "job": {
        "content": [
            {
                "reader": {
                    "name": "mysqlreader",
                    "parameter": {
                        "column": [],
                        "connection": [
                            {
                                "jdbcUrl": ["jdbc:mysql://localhost:3306/mock"],
                                "table": ["user_info"]
                            }
                        ],
                        "password": "root@123",
                        "username": "root",
                        "where": ""
                    }
                },
                "writer": {
                    "name": "streamwriter",
                    "parameter": {
                        "encoding": "UTF-8",
                        "print": true
                    }
                }
            }
        ],
        "setting": {
            "speed": {
                "channel": 1
            }
        }
    }
}
```

### mysql2hdfs
```json
{
    "job": {
        "content": [
            {
                "reader": {
                    "name": "mysqlreader",
                    "parameter": {
                        "connection": [
                            {
                                "jdbcUrl": ["jdbc:mysql://localhost:3306/db1", "jdbc:mysql://localhost:3306/db2"],
                                "table": ["table1", "table2", "table3"],
                                "querySql": ["自定义sql,此时mysqlreader会直接忽略table、column、where"]
                            }
                        ],
                        "username": "root",
                        "password": "root",
                        "column": ["*表示所有列"],
                        "splitPk": "数据分片字段,只支持整形,一般是主键,因为主键比较均匀不会数据倾斜,此时datax会启动并发任务提高效率",
                        "where": ""
                    }
                },
                "writer": {
                    "name": "hdfswriter",
                    "parameter": {
                        "column": [
                          {
                            "name": "hello",
                            "type": "string"
                          }
                        ],
                        "compress": "hdfs文件压缩类型,默认不压缩",
                        "defaultFS": "namenode地址,hdfs://${ip}:${port}",
                        "fieldDelimiter": "字段分隔符",
                        "fileName": "文件名",
                        "fileType": "文件类型text/orc",
                        "path": "hdfs路径",
                        "writeMode": "写入模式append/nonConflict/truncate"
                    }
                }
            }
        ],
        "setting": {
            "speed": {
                "channel": 1,
                "record": 100000
            }
        }
    },
    "core":{
        "transport": {
            "channel": {
                "speed": {
                    "record": 10000
                }
            }
        }
    }
}
```