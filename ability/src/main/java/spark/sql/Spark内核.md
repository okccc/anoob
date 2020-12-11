<!--1. SparkSubmit-->
<!--    // 启动进程-->
<!--    -- main-->
<!--        // 封装参数-->
<!--        -- new SparkSubmitArguments-->
<!--        // 提交-->
<!--        -- submit-->
<!--            // 准备提交环境-->
<!--            -- prepareSubmitEnvironment-->
<!--                // Cluster集群模式(重点)-->
<!--                -- childMainClass = "org.apache.spark.deploy.yarn.Client"-->
<!--                // Client客户端模式-->
<!--                -- childMainClass = args.mainClass (比如SparkPi)-->
<!--            -- doRunMain (runMain)-->
<!--                // 反射加载类-->
<!--                -- Utils.classForName(childMainClass)-->
<!--                // 查找main方法-->
<!--                -- mainClass.getMethod("main", new Array[String](0)<!-- @IGNORE PREVIOUS: link -->.getClass)-->
<!--                // 调用main方法-->
<!--                -- mainMethod.invoke-->

<!--2. Client-->
<!--    // 启动进程-->
<!--    -- main-->
<!--        // 封装参数-->
<!--        -- new ClientArguments(argStrings)-->
<!--        -- new Client-->
<!--            -- yarnClient = YarnClient.createYarnClient-->
<!--        -- client.run-->
<!--                -- submitApplication-->
<!--                    // 封装指令 command = bin/java org.apache.spark.deploy.yarn.ApplicationMaster (Cluster)-->
<!--                               command = bin/java org.apache.spark.deploy.yarn.ExecutorLauncher  (client)-->
<!--                    -- createContainerLaunchContext-->
<!--                    -- createApplicationSubmissionContext-->
<!--                    // 向Yarn提交应用,提交指令-->
<!--                    -- yarnClient.submitApplication(appContext)-->

<!--1. ApplicationMaster-->
<!--    // 启动进程-->
<!--    -- main-->
<!--        -- new ApplicationMasterArguments(args)-->
<!--        // 创建应用管理器对象-->
<!--        -- new ApplicationMaster(amArgs, new YarnRMClient)-->
<!--        // 运行-->
<!--        -- master.run-->
<!--            // Cluster-->
<!--            -- runDriver-->
<!--                // 启动用户应用-->
<!--                -- startUserApplication-->
<!--                    // 获取用户应用的类的main方法-->
<!--                    -- userClassLoader.loadClass(args.userClass).getMethod("main", classOf[Array[String]])-->
<!--                    // 启动Driver线程,执行用户类的main方法,-->
<!--                    -- new Thread().start()-->
<!--                // 注册AM-->
<!--                -- registerAM-->
<!--                    // 获取yarn资源-->
<!--                    -- client.register-->
<!--                    // 分配资源-->
<!--                    -- allocator.allocateResources()-->
<!--                        -- handleAllocatedContainers-->
<!--                            -- runAllocatedContainers-->
<!--                                -- new ExecutorRunnable().run-->
<!--                                    -- startContainer-->
<!--                                        // 封装指令  command = bin/java org.apache.spark.executor.CoarseGrainedExecutorBackend-->
<!--                                        -- prepareCommand-->

<!--1. CoarseGrainedExecutorBackend-->
<!--    // 启动进程-->
<!--    -- main-->
<!--        -- run-->
<!--            -- onStart-->
<!--                -- ref.ask[Boolean](RegisterExecutor-->
<!--            -- receive-->
<!--                --  case RegisteredExecutor-->
<!--                    -- new Executor-->