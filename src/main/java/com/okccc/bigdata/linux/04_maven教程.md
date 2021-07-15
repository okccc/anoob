### maven常用命令
```shell script
# maven是一款自动化构建工具,用于项目的构建和依赖管理
mvn -v             # 查看maven版本
mvn compile        # 编译项目源代码(多了target目录)
mvn test-compile   # 编译测试源代码(查看target目录变化)
mvn test           # 运行测试类,测试代码不会被打包或部署(查看target目录变化)
mvn package        # 将编译好的代码打包成可发布的格式,比如jar(查看target目录变化)
mvn install        # 将打好的jar包安装到本地maven仓库,可以让其它工程依赖(查看本地仓库目录变化)
mvn install:install-file -DgroupId=<自定义> -DartifactId=<自定义> -Dversion=<自定义> -Dpackaging=jar -Dfile=<绝对路径>
mvn clean package  # 编译项目并打jar包 -Dmaven.test.skip=true 表示跳过测试代码的编译和运行
mvn clean install  # 打完包后部署到本地仓库
mvn clean deploy   # 打完包后部署到本地仓库和远程仓库

# scope控制依赖jar包的使用范围
compile     # 参与项目的编译、测试、运行、打包,贯穿所有阶段,随项目一起发布(默认值)
provided    # 参与项目的编译、测试、运行,但是在打包阶段做了exclude,表示该依赖由jdk或服务器提供,避免jar包冲突,比如web开发的servlet-api.jar
runtime     # 不参与项目的编译,只参与测试和运行,比如JDBC驱动包是不需要编译的,运行时才会使用到
test        # 只参与项目的测试,比如Junit包
```

### maven配置文件
```xml
<!-- 使用阿里云镜像下载 -->
<mirrors>
  <mirror>
    <id>nexus-aliyun</id>
    <mirrorOf>central</mirrorOf>
    <name>Nexus aliyun</name>
    <url>http://maven.aliyun.com/nexus/content/groups/public</url>
  </mirror>
</mirrors>
<profiles>
     <profile>
          <id>jdk-1.8</id>
          <activation>
            <activeByDefault>true</activeByDefault>
            <jdk>1.8</jdk>
          </activation>
          <properties>
            <maven.compiler.source>1.8</maven.compiler.source>
            <maven.compiler.target>1.8</maven.compiler.target>
            <maven.compiler.compilerVersion>1.8</maven.compiler.compilerVersion>
          </properties>
     </profile>
</profiles>
```

### maven常见错误
```shell script
# scala项目打jar包后找不到类
mvn package只会对java源码进行编译和打包,将jar包改成rar压缩文件,或者直接查看target/classes发现scala类没打进来
pom文件scala-maven-plugin添加execution标签,或者手动执行 mvn clean scala:compile compile package
mvn package只会对java源码包下的java代码和scala源码包下的scala代码进行编译和打包,所以java和scala代码不要混在一块

# 类加载器读不到resources目录配置文件
maven打包默认会将java/resources/scala三个包的内容都打进去,查看target/classes发现资源文件没进来,idea环境出问题了

# idea使用maven插件打包没问题,mvn package打包提示jdk冲突
idea自带的maven版本是3.6.3,本地安装的maven版本是3.5.4,不同版本解决jar包冲突的方式不一样
brew install maven

# pom丢失依赖不可用
[WARNING] The POM for com.okccc:commons:jar:1.0-SNAPSHOT is missing, no dependency information available
https://www.cnblogs.com/li150dan/p/11114773.html

# maven jar包冲突
SLF4J: Class path contains multiple SLF4J bindings.
SLF4J: Found binding in [jar:file:/Users/okc/.m2/repository/org/slf4j/slf4j-log4j12/1.6.1/slf4j-log4j12-1.6.1.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: Found binding in [jar:file:/Users/okc/.m2/repository/ch/qos/logback/logback-classic/1.0.7/logback-classic-1.0.7.jar!/org/slf4j/impl/StaticLoggerBinder.class]
显示slf4j-log4j12包和logback-classic包冲突,其中logback-classic是我在pom文件里引入的,说明有别的依赖引用了slf4j,找到它并在pom中排除
mvn dependency:tree  # 查看工程依赖关系

# Error: A JNI(Java Native Interface) error has occurred, please check your installation and try again
运行时使用的java和编译时使用的javac版本不一致,卸载java重装

# Error: Failed to execute goal net.alchim31.maven:scala-maven-plugin:4.4.0:compile (default) on project ability
scala-maven-plugin版本是4.4.0但是maven版本是3.6.3
```