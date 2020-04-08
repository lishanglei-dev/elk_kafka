# logback发送日志消息到kafka
    本项目主要通过ILoggingEvent的日志事件拦截日志,
    发送日志信息到卡夫卡集群,
    然后通过ELK 框架将日志可视化界面进行展示
    jdk:1.8.0_241
    kafka:2.4.1
    elasticsearch+logstash+kibana:6.8.8
    springboot:2.2.6.RELEASE
###pom依赖
    注意依赖版本与kafka服务器集群版本一致
       <dependency>
                <groupId>org.apache.kafka</groupId>
                <artifactId>kafka_2.11</artifactId>
                <version>2.4.1</version>
                <exclusions>
                    <exclusion>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-log4j12</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
###自定义日志拦截类发送消息到kafka
    import ch.qos.logback.classic.spi.ILoggingEvent;
    import ch.qos.logback.core.AppenderBase;
    import lombok.Data;
    import lombok.extern.slf4j.Slf4j;
    import org.apache.kafka.clients.producer.KafkaProducer;
    import org.apache.kafka.clients.producer.Producer;
    import org.apache.kafka.clients.producer.ProducerRecord;
    
    import java.util.Properties;
    
    @Slf4j
    @Data
    public class KafkaAppender extends AppenderBase<ILoggingEvent> {
    
        private String bootstrapServers;
    
        //kafka生产者
        private Producer<String, String> producer;
    
        @Override
        public void start() {
            super.start();
            if (producer == null) {
                Properties props = new Properties();
                props.put("bootstrap.servers", bootstrapServers);
                //判断是否成功，我们指定了“all”将会阻塞消息
    //    props.put("acks", "all");
                props.put("retries", 0);
                props.put("batch.size", 0);
                //延迟1s，1s内数据会缓存进行发送
                props.put("linger.ms", 1);
                props.put("buffer.memory", 33554432);
                props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                producer = new KafkaProducer<String, String>(props);
            }
        }
    
        @Override
        protected void append(ILoggingEvent eventObject) {
            String msg = eventObject.getFormattedMessage();
            log.debug("向kafka推送日志开始:" + msg);
            ProducerRecord<String, String> record = new ProducerRecord<String, String>(
                    "kafka", msg, msg);
            producer.send(record);
        }
    
    }
###自定义日志配置文件logback.xml
    <?xml version="1.0" encoding="utf-8"?>
    <configuration scan="true" scanPeriod="60 seconds" debug="false">
        <appender name="KAFKA" class="com.example.kafka.appender.KafkaAppender">
            <bootstrapServers>192.168.8.104:9093</bootstrapServers>
        </appender>
        <property name="LOG_HOME" value="/tmp/logs/mservice-zuul"/>
    
        <!-- Console 输出设置 -->
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder charset="UTF-8">
                <!--  显示毫秒数
                    <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n</pattern>-->
                <pattern>%d{HH:mm:ss} %-5level [%thread] %logger - %msg%n</pattern>
            </encoder>
        </appender>
    
        <!-- 每天产生一个文件 -->
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <!--日志文件输出的文件名-->
                <FileNamePattern>${LOG_HOME}/mservice-zuul.%d{yyyy-MM-dd}.log</FileNamePattern>
                <!--日志文件保留天数-->
                <MaxHistory>15</MaxHistory>
            </rollingPolicy>
            <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                <!--格式化输出：%d表示日期，%thread表示线程名，%-5level：级别从左显示5个字符宽度%msg：日志消息，%n是换行符-->
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n</pattern>
            </encoder>
        </appender>
    
        <!--将上面的appender添加到root-->
        <root level="INFO">
            <appender-ref ref="STDOUT"/>
            <appender-ref ref="FILE"/>
        </root>
        <logger name="kafka_logger" additivity="false">
            <appender-ref ref="STDOUT"/>
            <appender-ref ref="FILE"/>
            <appender-ref ref="KAFKA"/>
        </logger>
    
    </configuration>
##环境搭建
    								kafka+ELK
    1.kafka概述:
    	kafka是一种消息队列,数据读写存储服务,可以缓存一部分数据
    点对点模式:一对一,消费者主动拉取数据消息收到后消息清除
    	是一个基于拉去或者轮询的消息传送模型,这种模型从队列中请求信息,而不是将消息推送到客户端.这个模型的特点是发送到队列中的消息被一个且只有一个接收者处理,即使优多个消息监听者也是如此
    发布订阅模式:一对多,数据产生后推送到所有订阅者
    	发布订阅模型则是一个基于推送的消息推送模型,发布订阅模型可以优多种不同的订阅者,临时订阅者只在主动监听主题时才接收消息,而持久订阅者则监听主题的所有消息,即使当前订阅者不可用,处于离线状态
    	
    消息队列的特点:
    	1.解耦
    	2.冗余(消息队列把数据持久化直到被完全处理,防止消息丢失)
    	3.拓展性
    	4.灵活性&峰值处理能力
    	5.可恢复性(副本机制,默认三份)
    	6.顺序保证
    	7.缓冲
    	8.异步通信
    2.什么是kafka?
    	在流式计算中,kafka一般用来 缓存数据,storm通过kafka的数据进行计算
    	1.Apache kafka是一个开源消息系统,由scala携程,是由Apache软件基金会开发的一个开源消息系统项目
    	2.kafka最初由linkedin公司开发,并于2011年初开源.2012年10月从Apache Incubator毕业,该项目的目标是为处理实时数据提供一个统一,高通量,低等待的平台.
    	3.kafka是一个分布式消息队列,kafka对消息保存时根据Topic进行归类,发送消息者称为Producer,消息接收者成为Consumer,此外kafka集群由多个kafka实例组成,每个实例(server)被称为broker
    	4.无论时kafka集群还是consumer都依赖于zookeeper集群来保存一些meta信息(元数据)来保证系统的可用性
    概念划分:生产者发送的消息按照主题进行划分,一个主题通常包含多个分区,这多个分区可以在一台broker上也可以在多台broker上.
    	producer发布一个消息"ABCDEFG"到一个主题topic,该主题由三个分区,则每个分区的内容为:"AB","CD","EF"
    3.生产者与分区:
    生产者将消息投放到分区的策略:
    	1.如果在发送消息时制定了分区,则消息投递到指定的分区
    	2.如果没有指定分区,但是消息的key不为空,则基于key的哈希值来选择一个分区
    	3.如果既没有指定分区,消息的key也为空,则采用轮询的方式来选择一个分区
    4.消费者组(cusmoter group)与分区
    消费者以组的名义订阅主题,主题由多个分区,消费者组中由多个消费实例.
    同一时刻,一个消息只能被族中的一个消费实例消费
    CG订阅这个主题,意味着该主题下的所有分区都会被组中的消费者消费到.如果按照从属关系来说,主题下的每个分区只从属于组中的一个消费者,不可能出现族中的两个消费者消费同一个分区.
    	如果分区数大于等于组中的消费者数,一个消费者可以消费多个分区.
    	如果消费者数多于分区数,则会造成一些消费者时多余的,一直接收不到消息.
    	如果要实现广播效果,即每个消费者都要消费到消息,则每个消费者组应只包含 一个消费者.
    	如果要实现单播效果,则多个消费者放到同一个消费者组中.
    	不同的消费者组可以消费同一主题的消息
    4.1消费者分区分配策略
    	自定义分配策略需要继承AbstractPartitionAssignor.默认由三种实现
    4.1.1.range(默认)	
    	实现类:angeAssignor	
    	可以通过消费者配置中的partition.assignment.strategy参数来指定分配策略,它的值是类的全路径数组
    	range策略是基于每个主题的
    	对于每个主题,我们以数字排序排列可用分区,以字典顺序排列消费者.然后将分区数量除以消费者总数,以确定分配给每个消费者的分区数量,如果没有平均划分(除不尽),那么最初的几个消费者将有一个额外的分区.
    
    	
    4.1.2roundrobin(轮询)
    实现类:RoundRobinAssignor
    
    
    无论消息是否被消费,kafka都会保存消息,有两种策略删除消息:
    	1.log.retentition.hours=168						7天
    	2.log.retentition.bytes=1073741824				1T
    5.kafka集群搭建
    	目前最新稳定版本为2.4.1 ,jdk版本1.8.0_241
    	5.1.下载tar包
    	wget https://mirrors.tuna.tsinghua.edu.cn/apache/kafka/2.4.1/kafka_2.12-2.4.1.tgz
    	5.2.解压tar包到/opt/module目录
    	tar zxvf kafka_2.12-2.4.1.tgz -C /opt/module
    	5.3.将解压后的包重命名为kafka
    	mv kafka_2.12-2.4.1 kafka
    	5.4.在opt/module/kafka目录下创建logs文件夹
    	cd kafka
    	mkdir logs
    	5.5.修改配置文件
    	cd config
    	vi server.properties
    	
    	#broker的全局唯一编号,不能重复
    	broker.id=1
    	port=9092
    	#删除主题功能
    	delete.topic.enable=true
    	#处理网络线程数
       	num.network.threads=3
    	#用来处理磁盘IO线程数
    	num.io.threads=8
    	#发送套接字缓冲区大小
    	socket.send.buffer.bytes=102400
    	#接收套接字的缓冲区大小	
    	socket.receive.buffer.bytes=102400
    	#请求套接字的缓冲区大小	
    	socket.request.max.bytes=104857600
    	#kafka运行日志存放路径
    	log.dirs=/opt/module/kafka/logs
    	#topic在当前broker上的分区个数
    	num.partitions=1
    	#用来恢复和清理data下数据的线程数量
    	num.recovery.threads.per.data.dir=1
    	#segment文件保留的最长时间,超时将被删除
    	log.segment.hours=168
    	#配置zookeeper集群地址
    	zookeeper.connect=127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
    	5.6.配置环境变量
    		vi /etc/profile
    		export KAFKA_HOME=/opt/module/kafka
    		export PATH=$PATH:$KAFKA_HOME/bin
    		source /etc/profile
    	5.7 复制kafka到其它两台机器的当前位置
    		scp -r kafka 127.0.01:$PWD
    		scp -r kafka 127.0.01:$PWD
    
    		同上,修改配置及环境变量
    	5.8相关命令
    	#指定配置文件后台启动kafka
    	kafka-server-start.sh  ../config/server.properties &
    	kafka-server-stop.sh
    	#主题相关
    	kafka-topics.sh
    	#查看当前服务器所有topic
    	kafka-topics.sh --zookeeper 127.0.0.1:2181 --list
    	#创建topic
    	kafka-topics.sh --zookeeper 127.0.0.1:2181 \--create --replication-factor 3 --partitions 1 	--topic kafka
    	#选项说明
    	--topic  定义topic名
    	--replication-factor 定义副本数
    	--partitions定义分区数
    	#删除topic 需要在server.properties中设置delete .topic.enable=true,否则只是标记删除	或直接重启
    	kafka-topics.sh --zookeeper 127.0.0.1:2181 \ --delete --topic kafka
    	#查看主题信息
    	kafka-topics.sh --zookeeper 127.0.0.1:2181 \ -describe --topic kafka
    	#发送消息
    	kafka-console-producer.sh \ --broker-list 127.0.0.1:9092 
    	--topic kafka hello world kafka kafka
    	#接收数据
    	./kafka-console-consumer.sh --bootstrap-server 		127.0.0.1:9092,127.0.0.1:9093,127.0.0.1:9094 --topic kafka
    	#从头接收 
    	--from beginning
    	#查看所有消费者分组
        ./kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9092 --all-groups --list
    6.zookeeper集群搭建
    6.1下载压缩包
    	wget https://downloads.apache.org/zookeeper
    	/zookeeper-3.6.0/apache-zookeeper-3.6.0-bin.tar.gz
    6.2解压到指定目录下
    	tar zxvf apache-zookeeper-3.6.0-bin.tar -C /opt/module
    6.3重命名zk
    	mv apache-zookeeper-3.6.0 zk1
    6.4 复制配置文件
    	cd conf
    	cp zoo_example.cfg zoo.cfg
    6.5编辑配置文件	
    	tickTime=2000
    	initLimit=10
    	syncLimit=5
    	clientPort=2181
    	dataDir=/opt/module/zk1/data
    	dataLogDir=/opt/module/zk1/dataLog
    	server.1=127.0.0.1:2888:3888
    	server.2=127.0.0.1:2889:3889
    	server.3=127.0.0.1:2890:3890
    6.6创建文件
    	cd /opt/module/zk1
    	mkdir dataLog
    	mkdir data 
    	touch myid
    6.7编辑myid
    	vim myid
    	填入服务器号   比如1
    6.8分发服务
    	在其它两台服务器重复以上操作,注意修改clientPort=2181配置
    	或将本服务的zk分发到其它服务器上
    	scp -r /opt/module/zk1/ 127.0.0.1:2181 /opt/module/zk2
    6.9启动服务
    	cd /opt/module/zk1/bin
    	sh zkServer.sh start
    7.ES集群搭建
    	对应jdk版本1.8.0_241 且不允许使用root用户启动
    7.4下载es
    
    wget 	https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-6.8.8.tar.gz
    7.2解压到/opt/module目录
    	tar zxvf elasticsearch-6.8.8.tar.gz -C /opt/module/
    7.3重命名
    	 mv elasticsearch-6.8.8/ elasticsearch
    7.4创建新用户elasticsearch
    7.4.1创建用户组：
    groupadd elasticsearch
    7.4.2创建用户加入用户组：
    useradd elasticsearch -d /opt/module  -g elasticsearch
    7.4.3设置ElasticSerach文件夹为用户elasticsearch所有：
    chown -R elasticsearch.elasticsearch /opt/module/elasticsearch
    7.4.4打开文件/etc/security/limits.conf
    #在文件最后面添加如下内容
    * soft nofile 65536
    * hard nofile 131072
    * soft nproc 2048
    * hard nproc 4096
    ================================================================
    解释：
    soft  xxx  : 代表警告的设定，可以超过这个设定值，但是超过后会有警告。
    hard  xxx  : 代表严格的设定，不允许超过这个设定的值。
    nofile : 是每个进程可以打开的文件数的限制
    nproc  : 是操作系统级别对每个用户创建的进程数的限制
    ================================================================
    7.4.5打开文件/etc/sysctl.conf，添加下面内容：
     vm.max_map_count=655360
    7.4.6加载sysctl配置，执行命令：
    sysctl -p
    7.4.7重启电脑，执行命令：
    reboot
    7.5启动ElasticSerach
    7.5.1切换到用户elasticsearch：
    su elasticsearch
    7.5.2启动
    cd /opt/module/elasticsearch
    ./bin/elasticsearch -d
    7.5.3执行curl命令检查服务是否正常响应：
    curl 127.0.0.1:9200
    
    7.6 elstaicsearch配置(可选)
    7.6.1备份配置文件
    	cd /config
    	cp elasticsearch.yml elasticsearch.yml.bak
    7.6.2 elasticsearch.yml增加如下配置
    cluster.name: elk
    node.name: elkyjssjm
    node.master: true
    node.data: true
    path.data: /data/elasticsearch/data
    path.logs: /data/elasticsearch/logs
    bootstrap.memory_lock: false
    bootstrap.system_call_filter: false
    network.host: 0.0.0.0
    http.port: 9200
    http.cors.enabled: true
    http.cors.allow-origin: "*"
    #discovery.zen.ping.unicast.hosts: ["192.168.246.234", "192.168.246.231","192.168.246.235"]
    #discovery.zen.minimum_master_nodes: 2
    #discovery.zen.ping_timeout: 150s
    #discovery.zen.fd.ping_retries: 10
    #client.transport.ping_timeout: 60s
    
    
    参数详解:
    cluster.name        集群名称，各节点配成相同的集群名称。
    node.name       节点名称，各节点配置不同。
    node.master     指示某个节点是否符合成为主节点的条件。
    node.data       指示节点是否为数据节点。数据节点包含并管理索引的一部分。
    path.data       数据存储目录。
    path.logs       日志存储目录。
    bootstrap.memory_lock       内存锁定，是否禁用交换。
    bootstrap.system_call_filter    系统调用过滤器。
    network.host    绑定节点IP。
    http.port       端口。
    discovery.zen.ping.unicast.hosts    提供其他 Elasticsearch 服务节点的单点广播发现功能。
    discovery.zen.minimum_master_nodes  集群中可工作的具有Master节点资格的最小数量，官方的推荐值是(N/2)+1，其中N是具有master资格的节点的数量。
    discovery.zen.ping_timeout      节点在发现过程中的等待时间。
    discovery.zen.fd.ping_retries        节点发现重试次数。
    http.cors.enabled               是否允许跨源 REST 请求，表示支持所有域名，用于允许head插件访问ES。
    http.cors.allow-origin              允许的源地址。
    7.6.3jvm.options设置
    设置JVM堆大小，一般设置为内存的一半，但最少2G
    sed -i 's/-Xms1g/-Xms2g/' /opt/module/elasticsearch/config/jvm.options
    创建ES数据及日志存储目录并修改属主和属组，与上面配置文件中的路径一一对应
    mkdir -p /data/elasticsearch/data       
    mkdir -p /data/elasticsearch/logs 
    chown -R elasticsearch:elasticsearch /data/elasticsearch  
     #给刚刚创建的目录修改属主和属组
    chown -R elasticsearch:elasticsearch /opt/module/elasticsearch
    7.6.4系统优化
    系统优化：
    1.增加最大进程数
     vim /etc/security/limits.conf    
    2.增加最大内存映射数
    vim /etc/sysctl.conf   
    #添加如下
    #elasticsearch用户拥有的内存权限太小，至少需要262144；
    m.max_map_count=262144 
    #表示最大限度使用物理内存，在内存不足的情况下，然后才是swap空间
    vm.swappiness=0			
    8.kibana安装
    8.1下载kibana	
    wget https://artifacts.elastic.co/downloads/kibana/kibana-6.8.8-linux-x86_64.tar.gz
    
    8.2解压
    tar zxvf  tar zxvf kibana-6.8.8-linux-x86_64.tar.gz -C /opt/module
    8.3重命名
    mv kibana-6.8.8-linux-x86_64 kibana
    
    8.4修改配置
    cd /opt/module/kibana/config
    vim kibana.yml
    添加如下配置:
    server.port: 5601 
    server.host: "192.168.8.104"  #本机
    elasticsearch.hosts: "http://192.168.8.104:9200" #ES节点
    kibana.index: ".kibana"
    xpack.reporting.encryptionKey: "a_random_string"
    
    ===============================================================================
    server.port kibana 服务端口，默认5601
    server.host kibana 主机IP地址，默认localhost
    elasticsearch.url  用来做查询的ES节点的URL，默认http://localhost:9200
    kibana.index       kibana在Elasticsearch中使用索引来存储保存的searches, visualizations和dashboards，默认.kibana
    8.5启动(http://192.168.8.104:5601)
    ./..bin/kibana
    9.logstash安装
    9.1下载logstash
    wget https://artifacts.elastic.co/downloads/logstash/logstash-6.8.8.tar.gz
    9.2解压到指定目录
    tar zxvf logstash-6.8.8.tar.gz -C /opt/module
    9.3重命名
    mv logstash-6.8.8/ logstash
    9.4配置
    cd config
    cp logstash-sample.conf core.conf
    input {
    	kafka{
            bootstrap_servers => ["192.168.8.104:9092,192.168.8.104:9093,192.168.8.104:9094"]
            client_id => "kafka"
            group_id => "kafka"
            auto_offset_reset => "latest"
            consumer_threads => 5
            decorate_events => false
            topics => ["kafka"]
            type => "KAFKA"
          }
    }
    
    output {
      elasticsearch {
        hosts => ["http://192.168.8.104:9200"]
        index => "%{[@metadata][beat]}-%{[@metadata][version]}-%{+YYYY.MM.dd}"
      }
    }
    9.5启动
    cd bin
    sh logstash -f ../config/core.conf --config.reload.automatic
