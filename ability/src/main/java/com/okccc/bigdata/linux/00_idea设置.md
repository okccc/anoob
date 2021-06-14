### idea快捷键
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
# idea断点调试
step over 下一步,如果是方法不进入方法体直接跳过,就像超级玛丽跳过水管
step into 下一步,如果是方法会进入方法体
step out 跳出方法体
resume program 恢复程序运行,但如果断点下面代码还有断点则会停在下一个断点处
stop 停止
mute breakpoints 使断点失效
view breakpoints 查看断点
Condition 右键断点可以设置条件,比如代码循环了100次可以输入条件直接跳到第60次,加快调试进度
```

### idea常用设置
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
# idea关闭自动更新
Intellij IDEA - Preferences - System Settings - Updates - 取消勾选Automatically check updates
# idea导入导出所有设置
File - Manage IDE Settings - Import/Export Settings
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
# 代码块用横线分开
Intellij IDEA - Preferences - Editor - General - Appearance - Show method separators
# idea添加jar包
File - Project Structure - Libraries - 点击+将lib目录下的jar包都添加进来
# pom文件查看依赖图表
pom.xml - 右键 - Diagrams - Show Dependencies
# idea调用方法时会在括号中补全2个空格
Intellij IDEA - Preferences - Editor - Code Style - Java/Scala - Spaces - Within - Method call parentheses
```

### idea常用插件
```shell script
Translation           # 翻译
Scala                 # scala
Lombok                # 简化javabean开发
Rainbow Brackets      # 彩虹括号
Material Theme UI     # 主题
Statistic             # 当前项目的统计信息
Mongo Plugin          # mongodb的可视化数据库工具  View --> Tool Windows --> Mongo Explorer
Regex Tester          # 正则测试
JProfiler             # 性能分析
Json Parser           # json解析
```

### chrome常用插件
```shell script
谷歌访问助手
AdGuard                  # 广告拦截
Clear Cache              # 清空缓存
Extensity                # 管理chrome插件
Enhanced Github          # 显式github上文件大小
FeHelper                 # 前端助手
Git History              # 炫酷的展示github中任意文件的修改历史
Google translation       # 可保存单词本
Isometric Contributions  # 渲染github贡献记录的等距像素视图(装X神器)
JSONView                 # json格式化
Momentum                 # 壁纸
Octotree                 # 在github左侧显式当前工程目录结构
OneTab                   # 打开网页列表节约内存
Postman                  # 调试requests请求
Proxy SwitchyOmega       # fiddler代理
Tab Activate             # 打开新标签页后自动跳转,chrome默认是不跳转的
Translate Man            # 翻译侠
uBlock Origin            # 网络请求过滤工具占用极低的内存和CPU
XPath Helper             # xpath助手
```