# 子查询大大增强了sql的查询能力,因为很多时候需要从结果集中获取数据,或者从同一个表中先计算得到一个结果,然后再与这个结果比较
# 单行子查询：子查询返回一行数据
use eshop;

# 1.单行子查询：
# 查询工资大于Tom的员工
select * from employee where salary > (select salary from employee where name = 'Tom');  # 子查询
select t1.* from employee t1 join employee t2 where t2.name = 'Tom' and t1.salary > t2.salary;  # 自连接
# 性能对比：子查询是对未知表进行查询后的条件判断,自连接是对已知表(自身)进行查询后的条件判断,因此大部分DBMS都对自连接做了优化,效率更高

# 查询工资大于149号员工的员工
select * from employee where salary > (select salary from employee where id = 149);

# 查询job_id与141号员工相同,salary比143号员工多的员工
select * from employee where job_id = (select job_id from employee where id = 141) and salary > (select salary from employee where id = 143);

# 查询与1号或8号员工的manager_id和department_id相同的其他员工
select * from employee where manager_id in (select manager_id from employee where id in (1,8)) and department_id in (select department_id from employee where id in (1,8)) and id not in (1,8);

# HAVING中的子查询
# 查询最低工资大于50号部门最低工资的部门和其最低工资
select department_id,min(salary) from employee group by department_id having min(salary) > (select min(salary) from employee where department_id = 50);

# 查询平均工资大于公司平均工资的部门
select department_id from employee group by department_id having avg(salary) > (select avg(salary) from employee);