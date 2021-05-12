### shortcut
```shell script
# idea调整内存大小
Help - Edit Custom VM Options - -Xms256m(idea开始内存,提高启动速度)/-Xmx3072m(idea最大内存,提高性能)
# idea查看变量/方法/构造器的Structure时,开着的绿色锁表示public权限,关闭的红色锁表示private权限
# idea默认给被重新分配地址的变量或参数加下划线,因为有些代码很长,你很难知道变量是否被重新分配过地址值
# idea快捷键设置
Intellij IDEA - Preferences - Keymap - Main menu - Edit/Navigate/Run
# 查找某个类
Ctrl + Shift + Alt + N
# 重构代码
Ctrl + Shift + Alt + T
# 重命名类/变量/方法,注意生效范围
Alt + Shift + R
# 查看接口实现类
Ctrl + Alt + B
# 返回上一个光标
Alt + <- | ->
# 查看类的继承体系
F4
# 查看方法调用树(被调/主调)
Ctrl + Alt + *H*
# 快速跳转
Alt + 数字
# 折叠代码块
Ctrl + -
# duplicated code fragment
Intellij IDEA - Preferences - Editor - Inspections - General - Duplicated Code fragment
# dangling javadoc comment
Intellij IDEA - Preferences - Editor - Inspections - Java - Javadoc - Dangling Javadoc comment
# Edit Configurations
Run - Edit Configurations - Templates - Temporary configurations limit
# idea取消文档渲染
Intellij IDEA - Preferences - Editor - General - Appearance/Reader Mode - 取消勾选Rendered documentation comments
# idea删除文件恢复
Project/Module - 右键 - Local History - Show History - 找到删除文件 - Revert Selection
# 快速生成同步代码块
选中代码 - Code - Surround With - try/catch | synchronized ...
# idea设置代码模板
Intellij IDEA - Preferences - Editor - File and Code Templates - Includes - FileHeader Author/Date/Desc
# idea设置实时模板
Intellij IDEA - Preferences - Editor - Live Templates - 选中模板比如plain或者新增模板 - 编辑模板内容 - Change指定模板适用范围
# idea导入导出所有设置
File - Manage IDE Settings - Import/Export Settings
# 代码块用横线分开
Intellij IDEA - Preferences - Editor - General - Appearance - Show method separators
# idea添加jar包
File - Project Structure - Libraries - 点击+添加,直接复制到工程的lib目录是无效的
```

### idea-scala
```shell script
# idea安装Scala插件
Intellij IDEA - Preferences - Plugins - Scala
# idea配置maven环境
Intellij IDEA - Preferences - Build - Build Tools - Maven
# 创建maven工程
File - New - Project - Maven Next - GroupId/ArtifactId - Finish
# 给工程添加scala支持,不然无法创建scala类,查看scala源码会出现Decompile to java和Choose Sources
Project/Module - Add Framework Support - Scala - Use library scala-sdk-2.11.8/Create
File - Project Structure - Global Libraries - scala-sdk-2.11.8 - Add to Modules
# Project和Module
Intellij IDEA中Project是顶级结构单元,一个Project由一个或多个Module组成
# 管理Project中的Module
File - Project Structure - Modules - add/delete module - 此时module还在磁盘上,右键发现出现delete按钮了
# idea断点调试
step over 下一步,如果是方法不进入方法体直接跳过,就像超级玛丽跳过水管
step into 下一步,如果是方法会进入方法体
step out 跳出方法体
resume program 恢复程序运行,但如果断点下面代码还有断点则会停在下一个断点处
stop 停止
mute breakpoints 使断点失效
view breakpoints 查看断点
Condition 右键断点可以设置条件,比如代码循环了100次可以输入条件直接跳到第60次,加快调试进度
# idea关闭自动更新
Intellij IDEA - Preferences - System Settings - Updates - 取消勾选Automatically check updates
```

### maven
```shell script
# maven是一款自动化构建工具,用于项目的构建和依赖管理
# maven常用命令
mvn -v             # 查看maven版本
mvn compile        # 编译项目源代码(查看根目录变化)  
mvn test-compile   # 编译测试源代码(查看target目录变化)  
mvn test           # 运行测试类,测试代码不会被打包或部署(查看target目录变化)  
mvn package        # 将编译好的代码打包成可发布的格式,比如jar(查看target目录变化)  
mvn install        # 将打好的包安装到本地仓库,可以让其它工程依赖(查看本地仓库目录变化)
mvn clean package  # 编译项目并打jar包 -Dmaven.test.skip=true 表示跳过测试代码的编译和运行
mvn clean install  # 打完包后部署到本地仓库
mvn clean deploy   # 打完包后部署到本地仓库和远程仓库

# pom丢失依赖不可用
[WARNING] The POM for com.okccc:commons:jar:1.0-SNAPSHOT is missing, no dependency information available
https://www.cnblogs.com/li150dan/p/11114773.html

# maven jar包冲突
SLF4J: Class path contains multiple SLF4J bindings.
SLF4J: Found binding in [jar:file:/Users/okc/.m2/repository/org/slf4j/slf4j-log4j12/1.6.1/slf4j-log4j12-1.6.1.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: Found binding in [jar:file:/Users/okc/.m2/repository/ch/qos/logback/logback-classic/1.0.7/logback-classic-1.0.7.jar!/org/slf4j/impl/StaticLoggerBinder.class]
显示slf4j-log4j12包和logback-classic包冲突,其中logback-classic是我在pom文件里引入的,说明有别的依赖引用了slf4j,找到它并在pom中排出
mvn dependency:tree  # 查看工程依赖关系
```