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

### 参数配置
```shell
job.setting.speed.channel  # 全局channel并发数
job.setting.speed.record  # 全局channel的record限速
job.setting.speed.byte  # 全局channel的byte限速
core.transport.channel.speed.record  # 单个channel的record限速
core.transport.channel.speed.byte  # 单个channel的byte限速

# 提升DataX Job的Channel并发数
# 并发数 = TaskGroup数量 * 每个TaskGroup的Task数(默认5个并发执行conf/core.json)
# 1.record限速
# Channel个数 = job.setting.speed.record / core.transport.channel.speed.record
# 2.byte限速
# Channel个数 = job.setting.speed.byte / core.transport.channel.speed.byte
# 3.直接配置Channel个数
# 上面两个参数同时设置时取值小的作为最终的Channel数,如果没有上面两个参数job.setting.speed.channel才会生效

# 提高jvm堆内存
# 当提升DataX Job的Channel并发数时,内存占用会显著增加,因为DataX作为数据交换通道会在内存中缓存很多数据
# 例如Channel中会有一个Buffer作为临时的数据交换缓冲区,而在Reader和Writer中也会存在一些Buffer,为了防止OOM要将jvm堆内存调大为4G
# 可以直接更改datax.py的-Xms和-Xmx参数,或者在启动时加上对应参数 bin/datax.py --jvm="-Xms4G -Xmx4G" aaa.json
```