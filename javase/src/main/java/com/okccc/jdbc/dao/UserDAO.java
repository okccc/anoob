package com.okccc.jdbc.dao;

import com.okccc.jdbc.bean.User;
import com.okccc.jdbc.JdbcUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

public class UserDAO extends DAO{

    public static void main(String[] args) throws SQLException {
        Connection conn = JdbcUtil.getDruidConnection();
        User cus = new User(1, "grubby", "orc@qq.com", new Date(1234567890L));
//        addCustomer(conn, cus);
//        deleteById(conn, 19);
//        updateById(conn, cus);
        User user = getOne(1);
        System.out.println(user);
        List<User> users = getAll();
        users.forEach(System.out::println);
    }

    /**
     * 向customer表中插入一条数据
     */
    public static void addCustomer(Connection conn, User cus) {
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
    public static void updateById(Connection conn, User cus) {
        String sql = "update customers set name = ?, email = ?, birth = ? where id = ?";
        updateWithTx(conn, sql, cus.getName(), cus.getEmail(), cus.getBirth(), cus.getId());
    }

    /**
     * 查询单条记录
     */
    public static User getOne(int id) {
        String sql = "select id, name, email, birth from customers where id = ?";
        User user = queryOne(User.class, sql, id);
        return user;
    }

    /**
     * 查询所有记录
     */
    public static List<User> getAll() {
        String sql = "select id, name, email, birth from customers";
        List<User> users = queryList(User.class, sql);
        return users;
    }   
}