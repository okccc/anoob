package com.okccc.spark.common

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import org.apache.commons.pool2.impl.{DefaultPooledObject, GenericObjectPool, GenericObjectPoolConfig}
import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}

// 处理mysql查询结果的抽象接口
trait QueryCallback {
  def process(rs: ResultSet)
}

// mysql客户端代理对象
case class MySqlProxy(url: String, user: String, password: String, client: Option[Connection] = None) {

  // 获取mysql连接对象
  private val conn: Connection = client.getOrElse(DriverManager.getConnection(url, user, password))

  // 增删改
  def executeUpdate(sql: String, params: Array[Any]): Int = {
    var ps: PreparedStatement = null
    var rtn: Int = 0
    try {
      // 关闭自动提交
      conn.setAutoCommit(false)
      // 创建PrepareStatement对象
      ps = conn.prepareStatement(sql)
      // 给每个参数赋值
      if (params != null && params.length > 0) {
        for (i <- params.indices) {
          ps.setObject(i + 1, params(i))
        }
      }
      // 执行更新操作,返回受影响的行数
      rtn = ps.executeUpdate()
      // 手动提交
      conn.commit()
    } catch {
      case e: Exception => e.printStackTrace()
    }
    rtn
  }

  // 查询
  def executeQuery(sql: String, params: Array[Any], queryCallback: QueryCallback) {
    var ps: PreparedStatement = null
    var rs: ResultSet = null
    try {
      // 创建PrepareStatement对象
      ps = conn.prepareStatement(sql)
      // 给参数赋值
      if (params != null && params.length > 0) {
        for (i <- params.indices) {
          ps.setObject(i + 1, params(i))
        }
      }
      // 执行查询操作,返回结果集ResultSet
      rs = ps.executeQuery()
      // 处理查询结果
      queryCallback.process(rs)
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  // 批量执行sql
  def executeBatch(sql: String, paramsList: Array[Array[Any]]): Array[Int] = {
    var ps: PreparedStatement = null
    var rtn: Array[Int] = null
    try {
      // 关闭自动提交
      conn.setAutoCommit(false)
      // 创建PreparedStatement对象
      ps = conn.prepareStatement(sql)
      // 给参数赋值
      if (paramsList != null && paramsList.length > 0) {
        for (params <- paramsList) {
          for (i <- params.indices) {
            ps.setObject(i + 1, params(i))
          }
          // 插入批量参数
          ps.addBatch()
        }
      }
      // 执行批量sql,返回受影响的数组
      rtn = ps.executeBatch()
      // 手动提交
      conn.commit()
    } catch {
      case e: Exception => e.printStackTrace()
    }
    rtn
  }

  // 关闭mysql客户端
  def shutdown(): Unit = conn.close()
}

// 自定义对象池工厂类,负责对象的创建、包装和销毁
class PooledMySqlClientFactory(url: String, user: String, password: String, client: Option[Connection] = None)
  extends BasePooledObjectFactory[MySqlProxy] with Serializable {

  // 创建对象
  override def create(): MySqlProxy = MySqlProxy(url, user, password, client)

  // 包装对象
  override def wrap(obj: MySqlProxy): PooledObject[MySqlProxy] = new DefaultPooledObject(obj)

  // 销毁对象
  override def destroyObject(po: PooledObject[MySqlProxy]): Unit = {
    po.getObject.shutdown()
    super.destroyObject(po)
  }

}

// mysql池工具类
object MysqlPool {

  // 加载JDBC驱动,只需要一次
  Class.forName("com.mysql.jdbc.Driver")

  // org.apache.commons.pool2.impl中预设了三个可以直接使用的对象池：GenericObjectPool、GenericKeyedObjectPool和SoftReferenceObjectPool
  // GenericObjectPool的特点是可以设置对象池中的对象特征,包括LIFO方式、最大最小空闲数、有效性检查...
  private var genericObjectPool: GenericObjectPool[MySqlProxy] = _

  // 通过apply完成对象的创建
  def apply(): GenericObjectPool[MySqlProxy] = {
    // 单例模式
    if (this.genericObjectPool == null) {
      this.synchronized {
        // 获取MySql配置参数
        val url: String = ConfigurationManager.config.getString(Constants.JDBC_URL)
        val user: String = ConfigurationManager.config.getString(Constants.JDBC_USER)
        val password: String = ConfigurationManager.config.getString(Constants.JDBC_PASSWORD)
        val size: Int = ConfigurationManager.config.getInt(Constants.JDBC_DATASOURCE_SIZE)

        // 创建对象工厂
        val factory: PooledMySqlClientFactory = new PooledMySqlClientFactory(url, user, password)
        // 创建对象配置
        val config: GenericObjectPoolConfig = new GenericObjectPoolConfig()
        // 设置配置的最大对象数和最大空闲对象数
        config.setMaxTotal(size)
        config.setMaxIdle(size)
        // 返回一个GenericObjectPool对象池,对象池的创建需要工厂类和配置类
        this.genericObjectPool = new GenericObjectPool[MySqlProxy](factory, config)
      }
    }
    genericObjectPool
  }
}

