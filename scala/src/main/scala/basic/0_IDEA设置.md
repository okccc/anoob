### shortcut
```bash
# idea快捷键设置
File - Settings - Keymap
# 查找某个类
Ctrl + Shift + Alt + N
# 重构代码
Ctrl + Shift + Alt + T
# 查看接口实现类
Ctrl + Alt + B
# 返回上一个光标
Alt + <- | ->
# 查看类的继承关系图
Ctrl + Alt + U
# 查看方法调用树(被调/主调)
Ctrl + Alt + H
# 快速跳转
Alt + 数字
# duplicated code fragment
File - Settings - Editor - Inspections - General - Duplicated Code fragment
# remove dangling comment
File - Settings - Editor - Inspections - General - dangling javadoc comment
# Edit Configurations
Run - Edit Configurations - Templates - Temporary configurations limit
# idea删除文件恢复
Project/Module - 右键 - Local History - Show History - 找到删除文件 - Revert Selection
# idea查看变量/方法/构造器的Structure时,开着的绿色锁表示public权限,关闭的红色锁表示private权限
# 增强for循环快捷键 xxx.for
# 对于一些被重新分配地址的变量或者参数,IDEA默认给它们加上下划线,因为有些代码很长,你很难知道变量是否被重新分配过地址值
```

### idea-scala
```bash
# idea永久激活
install - Evaluate for free - Help - Edit Custom VM Options - 添加-javaagent:C:\Program Files\JetBrains\IntelliJ IDEA 2020.1.1\bin\jetbrains-agent.jar - 保存后重启 - Help - Register - Activation code - 输入激活码 - Activate
# idea安装Scala插件
File - Settings - Plugins - Scala
# idea配置maven环境
File - Settings - Build - Build Tools - Maven
# 创建maven工程
File - New - Project - Maven Next - GroupId(公司名)/ArtifactId(项目名) - Finish
# 给Project/Module添加scala支持,不然无法创建scala类
a.Project/Module - Add Framework Support - Scala - Use library scala-sdk-2.11.8/Create
b.File - Project Structure - Global Libraries - scala-sdk-2.11.8 - Add to Modules
# Project和Module
Intellij IDEA中Project是顶级结构单元,一个Project由一个或多个Module组成
# 管理Project中的Module
File - Project Structure - Modules - add/delete module
# 查看scala源码出现Decompile to java和Choose Sources
download scala-sources-2.11.x.tar.gz - File - Project Structure - Global Libraries - scala-sdk-2.11.8 - Add Sources
```

### maven
```bash
# maven是一款自动化构建工具,用于项目的构建和依赖管理
# maven常用命令
mvn -v            # 查看maven版本
mvn compile       # 编译项目源代码(查看根目录变化)  
mvn test-compile  # 编译测试源代码(查看target目录变化)  
mvn test          # 运行测试类,测试代码不会被打包或部署(查看target目录变化)  
mvn package       # 将编译好的代码打包成可发布的格式,比如jar(查看target目录变化)  
mvn install       # 将打好的包安装到本地仓库,可以让其它工程依赖(查看本地仓库目录变化)
```