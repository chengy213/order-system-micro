Q1: version15之前的版本会存在什么问题？
A: 因为用户可用缓存存在bug，导致下单成功或下单失败时有redis脏数据，在下单失败时必现！

Q2: 常见错误，点击下单按钮，提示下单失败：send message Exception
A：一般都是rocketmq集群问题，重启rocketmq集群即可，请使用rocketmq命令重启rocketmq服务