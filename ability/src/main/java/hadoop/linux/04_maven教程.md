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