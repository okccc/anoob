# 子查询大大增强了sql的查询能力,因为很多时候需要从结果集中获取数据,或者从同一个表中先计算得到一个结果,然后再与这个结果比较
# 单行子查询：子查询返回一行数据
# 多行子查询：子查询返回多行数据
# 关联子查询：子查询会使用主查询中的列
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

# CASE/IF中的子查询
# 查询员工的location,如果其department_id与Tom的department_id相同,则location为'Canada',其余则为'USA'
select case department_id when (select department_id from employee where name = 'Tom') then 'Canada' else 'USA' end as location from employee;
select if(department_id = (select department_id from employee where name = 'Tom'), 'Canada', 'USA') as location from employee;

# 2.多行子查询
# 查询其它部门比'IT'部门任一工资都低的员工
select * from employee where salary < any (select salary from employee where department_id = 'IT') and department_id <> 'IT';
select * from employee where salary < (select max(salary) from employee where department_id = 'IT') and department_id <> 'IT';

# 查询其它部门比'IT'部门所有工资都低的员工
select * from employee where salary < all (select salary from employee where department_id = 'IT') and department_id <> 'IT';
select * from employee where salary < (select min(salary) from employee where department_id = 'IT') and department_id <> 'IT';

# 查询工资最低的员工
select * from employee where salary = (select min(salary) from employee);

# 查询平均工资最低的部门
select department_id from employee group by department_id having avg(salary) <= all (select avg(salary) from employee group by department_id);
select department_id from employee group by department_id having avg(salary) = (
    select min(avg_salary) from (select avg(salary) as avg_salary from employee group by department_id) t1
);

# 查询各个部门中最高工资最低的那个部门的最低工资
select min(salary) from employee where department_id = (
    select department_id from employee group by department_id having max(salary) = (
        select min(max_salary) from (select max(salary) max_salary from employee group by department_id) t1
    )
);

# 查询平均工资最高部门的manager信息
select * from employee where id = (
    select distinct manager_id from employee where department_id = (
        select department_id from employee group by department_id having avg(salary) = (
            select max(avg_salary) from (select avg(salary) avg_salary from employee group by department_id) t1
        )
    )
);

# 3.关联子查询
# 查询工资大于本部门平均工资的员工
select * from employee t1 where salary > (select avg(salary) from employee t2 where t1.department_id = t2.department_id);

# 查询公司中的管理者信息
select * from employee where id in (select distinct manager_id from employee);  # 子查询
select distinct t1.* from employee t1 join employee t2 where t1.id = t2.manager_id;  # 自连接

# 查询人数大于10的部门
select t1.department_name from department t1 where 10 < (select count(*) from employee t2 where t1.id = t2.department_id);