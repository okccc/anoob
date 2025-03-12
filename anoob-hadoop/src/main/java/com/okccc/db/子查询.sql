# 查询工资大于Tom的员工
select * from employee where salary > (select salary from employee where name = 'Tom');  # 子查询
select t1.* from employee t1 join employee t2 where t2.name = 'Tom' and t1.salary > t2.salary;  # 自连接
# 性能对比：子查询是对未知表进行查询后的条件判断,自连接是对已知表(自身)进行查询后的条件判断,因此大部分DBMS都对自连接做了优化,效率更高