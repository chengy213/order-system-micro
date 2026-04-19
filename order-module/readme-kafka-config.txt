[附录：配置实践详解]
服务端口：9092

# Producer 配置
# Kafka 生产者可靠性配置
spring.kafka.producer.acks=all
spring.kafka.producer.properties.enable.idempotence=true

参数	                                    默认值	    推荐值	        详细说明
acks	                                1	        all / -1	    确保Leader收到消息后，还需等待所有ISR（同步副本）确认才算成功。这是最高可靠性级别，可防止Leader宕机导致数据丢失。
enable.idempotence	                    false	    true	        开启幂等性，保证生产者在任何情况下（如网络重试）都不会向Broker重复写入消息。这是实现“精准一次”（Exactly Once）语义的基础。
max.in.flight.requests.per.connection	5	        1	            保证分区内消息绝对有序。设置为1时，任何时刻只有一个请求在传输，避免因重试导致乱序。
compression.type	                    none	    lz4 / zstd	    推荐使用lz4或zstd压缩消息，能显著减少网络传输和磁盘存储开销，尤其适合大消息量场景。
batch.size	                            16KB	    32KB - 1MB	    Producer会将发往同一分区的多条消息攒成一个批次发送。增大此值可减少网络请求数，提升吞吐量，但会消耗更多内存。
linger.ms	                            0ms	        5ms - 100ms	    延迟发送时间，用于在低负载下等待更多消息凑成批次。适当增大此值能有效提升吞吐，但对延迟有微秒级影响。
buffer.memory	                        32MB	    64MB - 1GB	    Producer用于缓存待发送消息的总内存。如果消息发送速度持续超过Kafka接收速度，此缓冲区会被填满，导致发送阻塞。增大此值可应对突发流量。
max.request.size	                    1MB	        5MB - 10MB	    控制单个Produce请求的最大字节数。如果单条消息或批次大于1MB，需同步增大此值。建议不要超过Broker的message.max.bytes 。
retries & retry.backoff.ms	          2147483647	Integer.MAX_VALUE	retries默认无限重试。retry.backoff.ms建议设为500ms - 1000ms，以避免在Broker恢复前无效狂刷。

🗄️ Broker（服务端）配置：奠定集群性能和稳定性的基石
参数	                            默认值	    推荐值	        详细说明
num.partitions	                1	        3 - 6或更多	    主题的默认分区数。分区是Kafka并行处理的核心，数量应不小于消费者组内的消费者数。公式：分区数 ≈ 目标吞吐量 / 单分区吞吐量。
default.replication.factor	    1	        3	            生产环境务必设为3，即每条消息都有3个副本。这确保了集群在最多2台Broker宕机时仍可提供服务。
min.insync.replicas	            1	        2	            配合acks=all使用。它定义了写入成功前必须成功同步的最小副本数。设为2且副本数为3时，允许最多1个副本失败。
unclean.leader.election.enable	false	    false	        设为false确保Leader选举只能在ISR（同步副本）中进行，宁可等待也不选用不同步的副本，彻底避免数据丢失风险。
log.segment.bytes	            1GB	        1GB - 5GB	    日志分段文件大小。增大此值可减少文件创建和关闭的频率，降低磁盘I/O开销，对大吞吐场景有利。
log.retention.hours	            168h	    72h - 168h	    日志保留时间。需根据业务合规要求和存储容量灵活设置。例如，日志型数据可缩短，而业务流水数据可能需要更长。
num.network.threads	            3	        4 - 8	        处理网络请求的线程数。推荐值约为 CPU核心数的2/3，可根据CPU负载和连接数微调。
num.io.threads	                8	        8 - 12	        处理磁盘I/O的线程数，通常设为磁盘数量的2-3倍。如果使用SSD且吞吐量高，可适当增加。
socket.send.buffer.bytes	    100KB	    128KB - 1MB	    Socket发送缓冲区大小。增大此值可提升网络吞吐能力，尤其适用于高延迟网络。


👂 Consumer（消费者）配置：高效且稳定地处理数据
参数	                        默认值	        推荐值	                详细说明
enable.auto.commit	        true	        false	                强烈建议关闭，改为在业务逻辑处理成功后手动提交Offset。这能精确控制消费进度，避免因自动提交导致数据丢失或重复。
max.poll.records	        500	            500 - 1000	            单次poll()调用返回的最大消息数。若每条消息处理较慢，应减小此值，确保在max.poll.interval.ms内能完成处理，防止消费者被踢出组。
max.poll.interval.ms	    300000ms	    300000ms - 600000ms	    两次poll()调用的最大间隔。如果业务处理可能超过5分钟，务必调大此值，避免消费者被误判为死亡并触发不必要的重平衡（Rebalance）。
session.timeout.ms	        45000ms	        45000ms - 60000ms	    消费者与Broker间的会话超时时间。值越小，Broker能越快发现消费者故障并触发重平衡，但过小会增加网络压力。
fetch.min.bytes	            1	            32KB - 1MB	            每次拉取请求的最小数据量。增大此值可减少网络请求数，提升吞吐量，但会增加延迟。
fetch.max.wait.ms	        500ms	        500ms - 1000ms	        当未达到fetch.min.bytes时，Broker最长等待时间。增大此值可提升吞吐，但会增加延迟，需根据业务权衡。
max.partition.fetch.bytes	1MB	            5MB - 10MB	            每个分区返回的最大数据量。建议至少能容纳几十到几百条消息，避免因单条消息过大导致无法消费。
heartbeat.interval.ms	    3000ms	        3000ms - 5000ms	        消费者向Broker发送心跳的频率。通常设为session.timeout.ms的 1/3，既保证组管理稳定，又避免网络开销过大。
auto.offset.reset	        latest	        earliest / latest	    earliest从最早的消息开始消费；latest（默认）从最新的消息开始。新消费者首次接入时通常设为earliest以获取全量数据。


Q1: springboot应用重启后，在没有改任何代码和配置的情况下，能保证继续从原有offset消费Kafka数据吗？底层原理是怎么实现的？
Spring Boot 应用重启后，只要不修改代码和配置，就能保证从上次中断的 offset 继续消费。这背后的核心在于，消费者的消费进度（Offset）是被 Kafka 集群持久化存储的，与消费者的生命周期无关。

💡 Offset：如何“记住”消费到了哪里？
Kafka 使用一个内部的特殊 Topic，名为 __consumer_offsets，来集中存储所有消费者组的消费进度。它的工作流程如下：
- 存储格式：它像一个巨大的哈希表，里面的键 (Key) 是由三部分组成的三元组：
    - 消费者组名 (Group ID)
    - 主题名 (Topic)
    - 分区号 (Partition)
    对应的值 (Value)，就是该消费者组在这个分区上最新提交的 Offset。
- 可靠存储：__consumer_offsets 是一个普通的 Kafka 主题，同样具有多副本机制，并且数据会持久化到磁盘上。因此，即使 Broker 重启，只要还有健康的副本存在，Offset 信息就不会丢失。
- 高效检索：为了能快速获取 Offset，每个 Broker 都会在内存中缓存自己负责的那部分 Offset 信息，以便在消费者请求时能快速响应。

🚀 重启恢复的完整流程
当你的 Spring Boot 应用重启后，消费者会自动执行以下步骤来恢复消费：
- 加入消费者组：消费者启动后，会使用配置的 group.id 向 Kafka 集群发送加入请求。
- 查询 Offset：Kafka 会找到负责该消费者组的 Group Coordinator，从 __consumer_offsets 主题中查询该组最后一次提交的 Offset。
- 决定消费起点：
    - 情况一：查到了 Offset（最常见）。消费者会从该 Offset 位置继续消费。
    - 情况二：没查到 Offset（比如第一次启动，或 Offset 已过期）。此时，会根据 auto.offset.reset 配置来决定从哪里开始消费。常用配置包括：
        - earliest: 从最早的消息开始消费。
        - latest: 仅从最新的消息开始消费（默认值）。

💎 总结
因此，Spring Boot 应用重启后，数据处理的连续性是由 Kafka 稳健的内部设计保证的：
- 状态外部化：消费者的进度（Offset）由 Kafka 集群负责存储，与应用进程本身的生命周期解耦。
- 精准的坐标：通过 (Group ID, Topic, Partition) 这个唯一的三元组作为坐标，精确记录每个消费者的进度。
- 可靠的存储：__consumer_offsets 主题通过多副本和持久化机制，确保了这些“消费进度书签”的可靠性。
- 明确的恢复流程：消费者重启后，会有一个清晰的“查询 -> 定位 -> 消费”的恢复流程，确保从断点处继续。

Q2: Offset的值不会进行持久化吗？如果根据auto.offset.reset 配置来消费，不会存在消息丢失或重复消费的问题吗？
A: Offset 确实会被持久化，但它是有期限的。auto.offset.reset 就是为了应对 Offset 因过期等原因“消失”时而准备的“安全网”或“紧急预案”。它不决定常规情况，而是处理意外。它的存在本身就是为了提供一种明确的、可预期的处理方式，避免系统行为完全失控。

🗑️ Offset 为什么会过期？

Kafka 将每个消费者组的消费进度（Offset）持久化在内部主题 __consumer_offsets 中。但这个“进度书签”不是永久保留的，Kafka 会通过一个专门的配置项来管理它的生命周期：
- 过期策略：当一个消费者组变为空（Empty），即组内没有任何活跃消费者后，其提交的 Offset 并不会立刻消失。Kafka 会为其设置一个保留计时器，超时后才会被清理。
- 默认时间：这个 Offset 的保留时间，由 Broker 端的参数 offsets.retention.minutes 控制。在较新版本的 Kafka 中，默认值通常为 7 天。
- 具体行为：如果消费者组长期处于不活跃状态（没有消费者实例），超过保留期限后，Kafka 会自动删除该组所有已提交的 Offset。

当你的 Spring Boot 应用因业务调整或测试需求，长达数周甚至更久没有启动后，它的消费者组就可能被 Kafka 判定为“非活跃”并清理掉 Offset。当应用再次启动时，由于找不到之前的 Offset，便会触发 auto.offset.reset 配置的生效机制。

⚙️ auto.offset.reset 的三种策略与潜在风险
为了解决 Offset 丢失或无效的情况，Kafka 提供了 auto.offset.reset 配置，定义了三种明确的策略。你的疑问很准确：这几种策略都可能带来消息丢失或重复消费的风险，但它们的设计初衷，正是在无法保证“恰好一次”的极端情况下，提供一个明确且可预期的行为选择。

配置选项	            行为描述	                                                            风险
earliest	从头开始消费。消费者会从该分区有效数据的最早一条（LogStartOffset）开始读取。	        重复消费风险：可能导致大量的历史消息被重新消费一次。
latest	    只从最新消息开始。消费者会忽略所有历史消息，只从消费者启动后新到达的消息开始读取。	    数据丢失风险：会错过在消费者组不活跃期间（或Offset过期后）产生的所有消息。
none	    不自动重置，直接报错。如果找不到有效的 Offset，
            Kafka 会直接抛出 NoOffsetForPartitionException 异常，应用程序启动失败。	        应用启动失败：适用于对数据一致性要求极高，不能容忍任何数据丢失或重复消费的场景。 需要手动介入处理异常。

总的来说，auto.offset.reset 是 Kafka 在面对 Offset 丢失等异常情况时的“紧急预案”。理解这些配置的差异和潜在影响，可以帮助你根据业务场景（是更怕丢数据，还是更能容忍重复）做出明智的选择