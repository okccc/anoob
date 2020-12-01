### string
```sql
-- 长度
hive> select length('abcedfg');
-- 反转
hive> select reverse('abcedfg');
-- 拼接
hive> select concat('abc','def','gh');
-- 使用分隔符拼接
hive> select concat_ws(',','abc','def','gh');
-- split：将字符串切割成数组
hive> select split('abtcdtef','t');
-- collect_list/set：行转列,去重/不去重
hive> select concat_ws(',',collect_list(col));
-- sort_array：对数组元素排序
hive> select concat_ws(',',sort_array(collect_list(col)));
-- explode：列转行,将array[]或map{}拆分成多行
hive> select id,score from grade lateral view explode(scores) grade as score;
-- 去空格
hive> select trim(' abc ');
-- 截取
hive> select substr('facebook',3);
hive> select substr('facebook',3,2);
-- 将字符串切割成键值对
hive> select str_to_map('aaa:11&bbb:22', '&', ':')['aaa'];
-- 空值处理
hive> select nvl(field,'-');
-- 索引
hive> select instr('abcde','c');
-- 替换
hive> select regexp_replace('2019-01-01','-','');
-- 抽取
hive> select regexp_extract('foothebar', 'foo(.*?)(bar)', 1);
hive> select regexp_extract('foothebar', 'foo(.*?)(bar)', 2);
hive> select regexp_extract('foothebar', 'foo(.*?)(bar)', 0);
-- 解析url
hive> select parse_url('http://facebook.com/path/p1.php?query=1', 'PROTOCOL');           -- http  
hive> select parse_url('http://facebook.com/path/p1.php?query=1', 'HOST');		         -- facebook.com​  
hive> select parse_url('http://facebook.com/path/p1.php?query=1', 'PATH');		         -- /path/p1.php​  
hive> select parse_url('http://facebook.com/path/p1.php?query=1', 'QUERY');		         -- query=1  
hive> select parse_url('http://facebook.com/path/p1.php?query=1', 'QUERY','query');	     --  1  
hive> ​select parse_url('http://facebook.com/path/p1.php?query=1', 'FILE');			     -- /path/p1.php?query=1​  
-- 解析json字符串
hive> select nvl(get_json_object(t.json,'$.timestamp'),'-');  
-- 使用java类中的方法
hive> select reflect('java.net.urldecoder','decode','...');      -- 中文解码  
hive> select reflect("java.lang.string", "valueof", 1)         	 -- 1  
hive> select reflect("java.lang.string", "isempty")              -- true  
hive> select reflect("java.lang.math", "max", 2, 3)              -- 3  
hive> select reflect("java.lang.math", "round", 2.5)             -- 3  
hive> select reflect("java.lang.math", "exp", 1.0)            	 -- 2.7182818284590455  
hive> select reflect("java.lang.math", "floor", 1.9)           	 -- 1.0  
-- ascii：返回字符串第一个字符的ascii码  ascii - gbk - unicode - utf8
hive> select ascii('abcde');  
-- repeat,返回重复n次后的str字符串
hive> select repeat('abc',5);  
-- lpad：字符串补位
hive> select lpad('abc',10,'td');  
hive> select rpad('abc',10,'td');  
```

### time
```sql
-- 当前日期/时间戳/unix格式时间戳
hive> select current_date/current_timestamp/unix_timestamp();
-- 通用格式转换
hive> select from_unixtime(unix_timestamp(dt, 'yyyymmdd'),'yyyy-mm-dd');
-- 截取year/month/day/hour/minute/second
hive> select year('2016-10-19 16:23:08');
-- 截取日期
hive> select to_date('2011-12-08 10:03:01');
-- 转换日期格式
hive> select date_format('2020-10-30','yyyy');
hive> select date_format('2020-10-30','yyyy-MM');
-- 当前星期的第几天,第一天是周日
hive> select dayofweek('2020-10-30');
-- 日期增加
hive> select date_add('2016-10-18',10);
-- 日起减少
hive> select date_sub('2016-10-18',10);
-- 日期差值
hive> select datediff('2016-10-19','2016-03-15');
-- 月份增加
hive> select add_months('2009-08-31', 1);
-- 两个日期间隔月数
hive> select months_between('2020-10-31','2020-05-10');
-- 某年/月第一天
hive> select trunc('2018-08-20','YEAR');
hive> select trunc('2018-10-24','MONTH');
-- 某月最后一天
hive> select last_day('2018-05-12');
-- 下一个su/mo/tu/we/th/fr/sa
hive> select next_day('2020-10-30','sa');
-- 上个月今天/第一天/最后一天
hive> select add_months(current_date,-1);
hive> select add_months(trunc(current_date,'MM'),-1);
hive> select add_months(last_day(current_date),-1);
-- 上个季度第一天/最后一天
hive> select case when month(current_date) in (1,2,3) then add_months(trunc(current_date,'YY'),-3)
                  when month(current_date) in (4,5,6) then trunc(current_date,'YY')
                  when month(current_date) in (7,8,9) then add_months(trunc(current_date,'YY'),3)
                  when month(current_date) in (10,11,12) then add_months(trunc(current_date,'YY'),6) end;
hive> select case when month(current_date) in (1,2,3) then date_sub(trunc(current_date,'YY'),1)
                  when month(current_date) in (4,5,6) then date_sub(add_months(trunc(current_date,'YY'),3),1)
                  when month(current_date) in (7,8,9) then date_sub(add_months(trunc(current_date,'YY'),6),1)
                  when month(current_date) in (10,11,12) then date_sub(add_months(trunc(current_date,'YY'),9),1) end;
-- 去年今天/第一天/最后一天
hive> select add_months(current_date,-12);
hive> select add_months(trunc(current_date,'YY'),-12);
hive> select date_sub(trunc(current_date,'YY'),1);
-- 一年中的周数
hive> select weekofyear('2016-10-19 12:13:25');
-- 计算某个日期是星期几(0~6对应星期日~星期六)
hive> select pmod(datediff(current_date,'2020-04-26'),7);  # 2020-04-26是计算日期前面的任一星期日
```

### math
```sql
-- round
hive> select round(3.5);  
hive> select round(3.1415926,4);  
-- floo
hive> select floor(3.1415926);  
-- ceil
hive> select ceil(3.1415926);  
-- rand
hive> select rand();  
0.5577432776034763  
-- exp：计算自然对数e的a次方
hive> select exp(2);  
-- ln：计算a的自然对数e
hive> select ln(7.38905609893065);  
-- log10：计算以10为底a的对数
hive> select log10(100);  
-- log2：计算以2为底a的对数
hive> select log2(8);  
-- log：计算以a为底b的对数
hive> select log(4, 256);  
-- pmod：计算a除以b的余数
hive> select pmod(9,4);  
-- pow：计算a的p次幂
hive> select pow(2,4);  
-- sqrt：计算a的平方根
hive> select sqrt(16);  
-- bin：计算a的二进制代表示
hive> select bin(7);  
-- hex：计算a的十六进制表示
hive> select hex(17);  
hive> select hex('abc');  
-- unhex；计算该十六进制字符串所代表的字符串
hive> select unhex('616263');  
-- conv：将数字从一个进制转换成另一个进制
hive> select conv(17,10,16);  
hive> select conv(17,10,2);  
-- abs：计算绝对值
hive> select abs(-3.9);  
-- sin：计算a的正弦值
hive> select sin(0.8);  
-- asin：计算a的反正弦值
hive> select asin(0.7173560908995228);  
-- cos：计算a的余弦值
hive> select cos(0.9);  
-- acos：计算a的反余弦值
hive> select acos(0.6216099682706644);
-- positive：返回a本身
hive> select positive(-10);  
-- negative：返回a的相反数
hive> select negative(-5);  
```

### over()
```sql
-- 聚合函数得到单个值,分析函数返回一个数据集,再分析数据集可以解决很多问题
-- 窗口/分析函数经常和over()结合使用,over指定条件
格式：function(arg1,arg2..) over([partition by(分组依据)] [order by(排序依据)] [window_clause(窗口条件)]) 
over()  -- 指定分析函数工作的的数据窗口大小,可能会随着行的变化而变化,partition by分区依据,order by排序依据
unbounded/current row/n preceding/n following  -- 起点/当前行/往前n行数据/往后n行数据
rows between unbounded preceding and current row  -- 不写window_clause时默认
rows between unbounded preceding and unbounded following  -- 不写order_by和window_clause时默认

-- topK
-- 选出每个学校,每个年级,分数前三的科目
select t.*
from
(
    select school,class,subjects,score,
           row_number() over (partition by school,class,subjects order by score desc) rn
    from scores
) t
where t.rn <= 3;

-- topK%
-- 求sale前10%的业务员和销售额
select t.*
from 
(
    select uid,sale,ntile(10) over(order by sale desc) as n from orders
) t 
where t.n = 1;
-- 在1亿条数据中找出前100万大的 -> 引申含义就是找出前1%大的
-- 1亿条数据分布在集群10个节点上,找出前1万大的 -> 先在每个节点找出前1万大的,这样数据总量减少为10万,然后再找出这10万条数据中排前1万的
select t.*
from 
(
    select num,ntile(100) over(order by num desc) as n from datas
) t
where t.n = 1;

-- 排序型
select uid,pv,  
       rank() over(partition by uid order by pv desc) as rn1,        -- 1,2,3,3,5
       dense_rank() over(partition by uid order by pv desc) as rn2,  -- 1,2,3,3,4
       row_number() over(partition by uid order by pv desc) as rn3   -- 1,2,3,4,5
from dw_log_info;

-- 累加型
select month,
       -- 按月份求和
       sum(amount) pay_amount,
       -- 所有月份累计和
       sum(sum(amount)) over(order by month) s1,
       -- 前3个月累计和
       sum(sum(amount)) over(order by month rows between 3 preceding and current row) s2,
       -- 前一月后一月和本月累计和
       sum(sum(amount)) over(order by month rows between 1 preceding and 1 following) s3,
       -- 前一月后一月本月平均值
       avg(sum(amount)) over(order by month rows between 1 preceding and 1 following) a1
from dw_pay_info        
group by month;    

-- 前后型
lag(col,n,default) over(...)   -- 窗口内往上第n行的col值,null就取default
lead(col,n,default) over(...)  -- 窗口内往下第n行的col值,null就取default
lag(salary, 1, 0) over(partition by uid order by month) as prev_sal     -- 环比,与上个月份进行比较 
lag(salary, 12, 0) over(partition by uid order by month) as prev_12_sal -- 同比,与上年度相同月份比较

-- 分组排序后
first_value(col) over(partition by ... order by ...)     -- 分组排序后第一行
last_value(col) over(partition by ... order by ...)      -- 分组排序后最后一行

-- 百分比型
- CUME_DIST over (order by ...)
- CUME_DIST over (partition by ... order by ...)
- PERCENT_RANK over(order by ...)
- PERCENT_RANK over(partition by ... order by ...)
```

### sql
```sql
-- 单个分组字段的聚合结果排序可以用order by limit,多个分组字段的聚合结果排序要用row_number
-- 店铺销售数据
shop  stat_date    money 
s1    2020-03-01    300   
s1    2020-03-02    0     
s1    2020-03-03    500      
-- 统计连续3天有销售额的店铺
-- 思路：用每天的日期减去行号,如果结果一样说明是连续几天的数据
select shop,date_sub(stat_date, rn),count(1)
from
     (select shop,stat_date,row_number() over(partition by shop order by stat_date) rn from sale where money > 0) t1
group by shop,date_sub(stat_date, rn)
having count(1) >= 3;
-- 统计比前一天销售额高的数据
select a.* from sale a cross join sale b on datediff(a.stat_date, b.stat_date) = 1 where a.money > b.money;
```

```sql
-- 用户访问数据
uid    stat_date    cnt        uid    mn    mn_cnt    total_cnt
u1     2017-01-21    5         u1    2017-01    11    11
u2     2017-01-23    6         u1    2017-02    12    23
u3     2017-01-22    8         u1    2017-03    25    48
-- 统计每个用户累计访问次数
select uid,mn,mn_cnt,sum(mn_cnt) over(partition by uid order by mn) total_cnt
from
     (
        select uid,mn,sum(cnt) mn_cnt
        from
             (select uid,date_format(stat_date,'yyyy-MM') mn,cnt from action) t1
        group by uid,mn
     ) t2
group by uid,mn,mn_cnt;
```

```sql
-- 店铺访问数据
shop    uid
a1      u1
b1      u2
-- 统计每个店铺访问次数top3的访客信息,输出店铺名称、访客id、访问次数
select shop,uid,cnt,row_number() over(partition by shop order by cnt desc) as rn
from
     (select shop,uid,count(1) cnt from visit group by shop,uid) t1
where rn <= 3;
```

```sql
-- 学生表和成绩表
create table student(id int comment '学号', name string comment '姓名', age int comment '年龄');
create table grade(id int comment '学号', cid string comment '课程号', score int comment '分数');
-- 删除学生表中除了id其它列都相同的冗余信息
delete from student where id not in (select min(id) from student group by name,age);
-- 查询有两门以上课不及格的学生的学号、姓名、年龄(可以在一个表中查出所有列的情况使用子查询即可)
select id,name,age from student where id in (select id from grade where score < 60 group by id having count(*) >= 2);
-- 查询平均成绩大于60分的学生的姓名、年龄、平均成绩(需要在多个表中才能查出所有列的情况使用join)
select s.name,s.age,g.avg_score from student s join (select id,avg(score) avg_score from grade group by id having avg_score > 60) g on s.id = g.id;
-- 查询没有'001'课程成绩的学生的姓名、年龄(否定条件问题可以先查询肯定条件,然后再not in)
select name,age from student where id not in (select id from grade where cid='001');
select s.name,s.age from student s left join (select id from grade where cid='001') g on s.id = g.id where g.id is null;
-- 查询至少有一门课程与学号为01的学生所学课程相同的学生的姓名、年龄
select name,age from student where id in (select distinct(id) from grade where cid in (select cid from grade where id = 01));
-- 查询有'001'和'002'这两门课程下,成绩排名前3的学生的姓名、年龄
select s.name,s.age from student s inner join 
(
    select id,sum(score) sum_score 
    from grade 
    where id in (select id from grade where cid='001' or cid='002' group by id having count(*)=2)
    group by id
    order by sum_score desc limit 3
) g on s.id = g.id;
-- 统计每门课程的及格人数和不及格人数,按'[<60]'/'[60~85]'/'[85~100]'分数段划分也是同理,判断不同列用if,判断同一列的不同值用case when
select cid,
       sum(if score >= 60 then 1 else 0) '及格',
       sum(if score < 60 then 1 else 0) '不及格'
from grade
group by cid;
-- 行列互换问题
id  cid  score    id  c01  c02  c03    id  c01  c02  c03
01  c01  85       01  85  0   0        01  85  88  89
01  c02  88       01  0   88  0        02  91  92  95
02  c02  91       01  0   0   89
02  c03  92
-- 显示所有学生的所有课程的成绩和平均成绩并从高到低排序
select id,
       max(if cid = 'c01' then score else 0) as c01,
       max(if cid = 'c02' then score else 0) as c02,
       max(if cid = 'c03' then score else 0) as c03,
       avg(score) as avg_score
from score
group by id
order by avg_score desc;
-- 交换相邻学生的座位,比如座位是1234,想要变成2143(交换数据问题可以从数据编号的奇偶性考虑)
select case when mod(座位号, 2) != 0 then 座位号 + 1
            when mod(座位号, 2) = 0 then 座位号 - 1 end as '交换后座位号'
from student;
-- 查询至少连续出现3次的成绩(只有一个表,遇到时间间隔问题可以使用自连接)
select a.score from score a,score b,score c 
where a.id = b.id - 1 and b.id = c.id - 1 and a.score = b.score and b.score = c.score;
-- 求留存率(N日留存率 = N日留存用户数/当日活跃用户数)
select uid,
       count(distinct uid) uv,
       count(distinct if day = 1 then uid else null end) / count(distinct uid) as 1_keep,
       count(distinct if day = 3 then uid else null end) / count(distinct uid) as 3_keep,
       count(distinct if day = 7 then uid else null end) / count(distinct uid) as 7_keep
from
    (
        select *,datediff(a.stat_date, b.stat_date) day 
        from
            (select a.uid,a.stat_date,b.stat_date from action a left join action b on a.uid = b.uid) t1
    ) t2
group by uid;
```

```sql
-- team表只有一个字段name,有4条记录a/b/c/d分别对应4个球队,两两之间比赛列出所有可能组合
select a.name,b.name from team a, team b where a.name < b.name;
```