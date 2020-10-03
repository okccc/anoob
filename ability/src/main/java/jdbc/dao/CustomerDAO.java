package jdbc.dao;

import jdbc.bean.Customer;
import jdbc.util.JDBCUtils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

public class CustomerDAO extends DAO{
    public static void main(String[] args) throws SQLException {
        Connection conn = JDBCUtils.getC3P0Connection();
        Customer cus = new Customer(1, "grubby", "orc@qq.com", new Date(1234567890L));
//        addCustomer(conn, cus);
//        deleteById(conn, 19);
//        updateById(conn, cus);
        Customer customer = getOne(1);
        System.out.println(customer);
        List<Customer> customers = getAll();
        customers.forEach(System.out::println);
    }

    /**
     * 向customer表中插入一条数据
     */
    public static void addCustomer(Connection conn, Customer cus) {
        String sql = "insert into customers(name,email,birth) values(?,?,?)";
        updateWithTx(conn, sql, cus.getName(), cus.getEmail(), cus.getBirth());
    }

    /**
     * 根据id删除数据
     */
    public static void deleteById(Connection conn, int id) {
        String sql = "delete from customers where id = ?";
        updateWithTx(conn, sql, id);
    }

    /**
     * 根据id修改记录
     */
    public static void updateById(Connection conn, Customer cus) {
        String sql = "update customers set name = ?, email = ?, birth = ? where id = ?";
        updateWithTx(conn, sql, cus.getName(), cus.getEmail(), cus.getBirth(), cus.getId());
    }

    /**
     * 查询单条记录
     */
    public static Customer getOne(int id) {
        String sql = "select id, name, email, birth from customers where id = ?";
        Customer customer = selectOne(Customer.class, sql, id);
        return customer;
    }

    /**
     * 查询所有记录
     */
    public static List<Customer> getAll() {
        String sql = "select id, name, email, birth from customers";
        List<Customer> customers = selectMany(Customer.class, sql);
        return customers;
    }
}
