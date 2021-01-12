### shortcut
```shell script
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
# idea导入导出所有设置
File - Manage IDE Settings - Import/Export Settings
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
File - Project Structure - Modules - add/delete module
```