Seata AT 模式底层原理（结合本场景）
1. 核心思想
- 两阶段提交（2PC） 的改进版，通过自动补偿实现，对业务代码无侵入。
- 第一阶段：执行本地事务，同时记录 undo_log（镜像数据）。
- 第二阶段：全局提交（异步删除 undo_log）或全局回滚（根据 undo_log 生成反向 SQL 恢复数据）。

2. 本场景执行流程

用户下单请求
    │
    ▼
Order Module（全局事务发起者）
    │
    ├── ① 开启全局事务，生成 XID
    ├── ② 执行本地事务（创建订单、扣减库存）
    │      ├── 数据库操作 → 同时插入 undo_log（记录前镜像和后镜像）
    │      └── 事务提交（此时数据已落盘，但被 Seata 锁定）
    ├── ③ 通过 Feign 调用 Pay Module
    │      ├── XID 通过请求头传递
    │      └── Pay Module 执行本地事务（如果涉及 DB，也会记录 undo_log）
    ├── ④ 根据所有分支事务的结果决定：
    │      ├── 全部成功 → 全局提交（异步删除 undo_log）
    │      └── 任一失败 → 全局回滚（根据 undo_log 生成补偿 SQL）
    └── ⑤ 返回结果给客户端

3. 关键组件
- TC（Transaction Coordinator）：Seata Server，负责协调全局事务状态。
- TM（Transaction Manager）：发起全局事务的模块（Order Module）。
- RM（Resource Manager）：参与分支事务的模块（Order Module 和 Pay Module）。

4. 为什么 @Transactional 失效？
- 本地 @Transactional 只能保证单个服务内的数据库操作原子性，无法控制远程调用。
- Seata 将分布式事务拆分为多个本地事务，通过全局事务 ID 关联，统一提交或回滚。

5. 隔离性与锁
- Seata AT 模式默认使用全局锁，防止脏写。
- 第一阶段提交后，数据被锁住（通过 undo_log 中的 xid 和行锁），其他全局事务不能修改，直到第二阶段释放。

四、验证分布式事务

测试用例 1：支付检查失败（数量 > 20）
- 用户下单 21 件商品 → Pay Module 返回 false → Order Module 抛出异常 → Seata 触发全局回滚。
- 检查数据库：订单表无新增记录，库存未扣减。

测试用例 2：正常下单（数量 ≤ 20）
- 全局事务提交，订单创建成功，库存扣减。

测试用例 3：模拟网络异常（关闭 Pay Module）
- Feign 调用失败 → 全局事务回滚，订单和库存操作被撤销。