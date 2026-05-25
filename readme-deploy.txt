[启动参数] 三个子项目/模块都需要添加该VM参数
-DNACOS_ENABLED=true

[部署依赖]
JDK17, mysql, redis, nacos, rocketmq, kafka, ES, prometheus, Grafana(Optional)
备注：
1. sentinel单cluster实例实现下，不需要在本机部署单独的sentinel jar应用，除非你想使用UI页面，可以使用sentinel自带的sentinel-dashboard
java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard.jar

2.其中nacos启动使用自定义命令：nacos-start
alias nacos-start='sudo sh /Users/allen/ai-infra/nacos/standalone/nacos-3.2.1-2026.03.30/bin/startup.sh -m standalone'
alias nacos-stop='sudo sh /Users/allen/ai-infra/nacos/standalone/nacos-3.2.1-2026.03.30/bin/shutdown.sh'

3.rocketmq启动使用自定义命令: (详细见本项目中的zshrc文件)
# rocketmq
export ROCKETMQ_HOME=/usr/local/rocketmq
export PATH=$PATH:$ROCKETMQ_HOME/bin

# RocketMQ 管理命令
export NAMESRV_ADDR="127.0.0.1:9876"

rocketmq() {
    local cmd=$1
    case $cmd in
        start)
            echo "Starting RocketMQ..."
            nohup sh $ROCKETMQ_HOME/bin/mqnamesrv > $ROCKETMQ_HOME/logs/namesrv.log 2>&1 &
            sleep 2
            nohup sh $ROCKETMQ_HOME/bin/mqbroker -n localhost:9876 -c $ROCKETMQ_HOME/conf/broker.conf autoCreateTopicEnable=true > $ROCKETMQ_HOME/logs/broker.log 2>&1 &
            echo "RocketMQ started."
            ;;
        stop)
            echo "Stopping RocketMQ..."
            sh $ROCKETMQ_HOME/bin/mqshutdown broker
            sh $ROCKETMQ_HOME/bin/mqshutdown namesrv
            echo "RocketMQ stopped."
            ;;
        status)
            # 检查 NameServer 和 Broker 进程是否存在
            if pgrep -f "mqnamesrv" > /dev/null; then
                echo "NameServer is running."
            else
                echo "NameServer is NOT running."
            fi
            if pgrep -f "mqbroker" > /dev/null; then
                echo "Broker is running."
            else
                echo "Broker is NOT running."
            fi
            ;;
        restart)
            rocketmq stop
            sleep 2
            rocketmq start
            ;;
        logs)
            echo "Tailing NameServer log..."
            tail -f $ROCKETMQ_HOME/logs/namesrv.log
            ;;
        *)
            echo "Usage: rocketmq {start|stop|restart|status|logs}"
            ;;
    esac
}


