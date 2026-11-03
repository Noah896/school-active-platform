# 签到核销链路梳理（verifyCheckIn）

## 1. 范围与目标

本文梳理“活动签到核销”完整业务链路，核心入口为活动管理端接口
`POST /activity/manage/{id}/check-in/verify`（对应 `ActivityController.verifyCheckIn`，
服务层实现 `ActivityServiceImpl.verifyCheckIn`）。

链路覆盖以下内容：

- 上游：签到凭证（voucher）从“报名成功”到“待核销”的生成与状态流转；
- 核心：`verifyCheckIn` 的鉴权、参数校验、幂等控制、业务核销校验与落库；
- 下游：核销成功后报名/凭证状态更新、审计记录、缓存失效、用户画像异步更新，以及管理端统计/记录/看板的数据来源。

代码主干：

```text
报名成功生成凭证
   → 负责人输入 展示码 / 凭证 ID
   → 校验活动 / 权限 / 时间窗口 / 凭证状态 / 幂等
   → 凭证 UNUSED → CHECKED_IN（条件更新防重）
   → 报名 check_in_status=1 / check_in_time / voucher_id
   → 写核销审计记录 + 失效签到缓存 + 异步刷新用户画像
```

## 2. 接口定义

| 项目 | 内容 |
| --- | --- |
| 接口 | `POST /activity/manage/{id}/check-in/verify` |
| 控制器 | [ActivityController.java](../src/main/java/com/campus/controller/ActivityController.java:169) |
| 服务接口 | [IActivityService.java](../src/main/java/com/campus/service/IActivityService.java:72) |
| 服务实现 | [ActivityServiceImpl.java](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:943) |
| 权限要求 | 拥有 `checkin:verify` 权限，且操作人必须是该活动创建者 |
| 角色来源 | `ACTIVITY_ADMIN`（活动负责人）默认具备 `checkin:verify`，见 [RbacConstants.java](../src/main/java/com/campus/utils/RbacConstants.java:80) |
| 幂等要求 | Header `Idempotency-Key` 必填，缺失直接失败 |

### 请求体

`ActivityCheckInVerifyDTO`（[源码](../src/main/java/com/campus/dto/ActivityCheckInVerifyDTO.java)）：

```json
{
  "voucherId": 583628425655222273,
  "displayCode": "4EPC4S4J"
}
```

`voucherId` 与 `displayCode` 二选一即可；两者都传时优先按 `voucherId` 查凭证。
使用 `displayCode` 时会先 `trim()` 再转大写后匹配。

### 成功响应

`ActivityCheckInResultDTO`（[源码](../src/main/java/com/campus/dto/ActivityCheckInResultDTO.java)）：

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "activityId": 3,
    "voucherId": 583628425655222273,
    "displayCode": "4EPC4S4J",
    "userId": 1012,
    "userNickName": "张三",
    "voucherStatus": "CHECKED_IN",
    "resultStatus": "SUCCESS",
    "message": "签到成功",
    "checkedInTime": "2026-04-22 18:19:09",
    "checkedInBy": 1010
  }
}
```

## 3. 完整调用链

```text
ActivityController.verifyCheckIn
  └─> ActivityServiceImpl.verifyCheckIn
        1. 权限校验（checkin:verify）
        2. 活动存在性校验
        3. 操作人 = 活动创建者
        4. 入参校验（凭证ID/展示码、Idempotency-Key）
        5. 计算请求指纹 fingerprint
        6. Redis 幂等：先读缓存，再 setIfAbsent 写 PROCESSING 锁
        7. doVerifyCheckIn 执行业务核销
        8. 无论成功/失败都把结果写回幂等缓存（TTL 10 分钟）
        └─> doVerifyCheckIn
              ├─ findVoucher：按 voucherId 或 displayCode 查凭证（带 Redis 展示码缓存）
              ├─ 凭证归属活动校验
              ├─ 报名记录必须存在且 status=SUCCESS
              ├─ 签到时间窗口校验（开始前30分钟 / 结束后30分钟）
              ├─ 凭证状态校验（仅 UNUSED 可继续）
              └─ completeCheckIn：CAS 更新凭证 → 更新报名 → 写审计 → 失效缓存 → touchUser
```

### verifyCheckIn 方法步骤

实现位于 [ActivityServiceImpl.java:943](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:943)：

| 步骤 | 校验/动作 | 失败返回 |
| --- | --- | --- |
| 1 | 权限：`checkin:verify` | `无权执行签到核销` |
| 2 | 活动存在 | `活动不存在` |
| 3 | 操作人是活动创建者 | `无权执行签到核销` |
| 4 | `dto` 非空，且 `voucherId` 或 `displayCode` 至少一个 | `请提供凭证ID或展示码` |
| 5 | Header `Idempotency-Key` 非空 | `Idempotency-Key不能为空` |
| 6 | 计算指纹并读取幂等缓存 | 命中缓存直接返回缓存结果 |
| 7 | `setIfAbsent` 写 `PROCESSING` 锁 | 未抢到锁：重新读缓存或返回“请求处理中” |
| 8 | `doVerifyCheckIn` 执行核销，异常兜底 | `签到核销失败，请稍后重试` |
| 9 | 把最终 `Result` 写回幂等缓存 | - |

## 4. 幂等设计（Redis）

相关常量：[RedisConstants.java](../src/main/java/com/campus/utils/RedisConstants.java:36)。

| Key | TTL | 用途 |
| --- | --- | --- |
| `activity:checkin:idempotency:{activityId}:{Idempotency-Key}` | 10 分钟 | 核销幂等锁 + 结果缓存 |
| `activity:voucher:display:{展示码}` | 1 天 | 展示码 → 凭证 ID 映射缓存 |

流程细节：

1. `buildCheckInFingerprint`（[源码:1413](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1413)）对
   `activityId + voucherId + displayCode`（展示码转大写）计算 MD5，作为请求指纹。
2. 幂等 key 只绑定“活动 ID + 客户端幂等 key”，因此相同 key 换参数会判冲突：
   `Idempotency-Key与请求参数冲突`。
3. `resolveCachedCheckInResult`（[源码:1309](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1309)）返回三种缓存态：
   - `PROCESSING` → `请求处理中，请稍后重试`；
   - 已成功 → 直接返回缓存中的 `data`；
   - 已失败 → 返回缓存的 `errorMsg`。
4. `setIfAbsent` 抢锁，锁值 `{fingerprint, status=PROCESSING}`；抢锁失败说明并发重复请求，二次读缓存确认结果。
5. `cacheCheckInResult`（[源码:1333](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1333)）最终把
   `SUCCESS`/`FAILED` + `success/errorMsg/data` 写回，10 分钟后过期。

幂等缓存的兜底目标是：同一 `activityId + Idempotency-Key` 的重试请求返回第一次的结果，
避免“同一凭证重复核销”产生多条成功记录。

## 5. 业务核销 doVerifyCheckIn

实现位于 [ActivityServiceImpl.java:1211](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1211)，
按顺序执行以下校验：

| 顺序 | 校验 | 通过条件 | 失败处理 |
| --- | --- | --- | --- |
| 1 | 凭证存在 | 按 `voucherId` 或 `displayCode` 找到凭证 | `resultStatus=INVALID_VOUCHER`，`凭证不存在或无效` |
| 2 | 凭证归属活动 | `voucher.activityId == 当前 activityId` | `resultStatus=ACTIVITY_MISMATCH`，`凭证与当前活动不匹配` |
| 3 | 报名有效 | 凭证对应报名记录存在且 `status=SUCCESS` | `resultStatus=NOT_REGISTERED`，`报名记录不存在或已失效` |
| 4 | 时间窗口-未开始 | `now >= eventStartTime - 30min` | `resultStatus=OUT_OF_WINDOW`，`未到签到时间窗口` |
| 5 | 时间窗口-已结束 | `now <= eventEndTime + 30min`，超时先使凭证 `UNUSED→EXPIRED` | `resultStatus=OUT_OF_WINDOW`，`已超过签到时间窗口` |
| 6 | 凭证状态 | `status == UNUSED` | 交给 `handleUnavailableVoucher` |
| 7 | 执行签到 | 走 `completeCheckIn` | 见下一节 |

### 时间窗口

- 可签到起点：`活动开始时间 - 30 分钟`；
- 可签到终点：`活动结束时间 + 30 分钟`；
- 常量见 [ActivityServiceImpl.java:174](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:174)
  （`CHECK_IN_OPEN_BEFORE_MINUTES=30`、`CHECK_IN_CLOSE_AFTER_MINUTES=30`）；
- 活动开始/结束时间为空时不拦截。

### 凭证状态分支（handleUnavailableVoucher）

实现位于 [ActivityServiceImpl.java:1291](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1291)：

| 凭证状态 | 返回 |
| --- | --- |
| `CHECKED_IN`（已签到） | `success=true`，`resultStatus=ALREADY_CHECKED_IN`，`message=该凭证已签到`（作为“幂等成功”返回，带原签到信息） |
| `CANCELED` | `success=false`，`凭证已取消` |
| `EXPIRED` | `success=false`，`凭证已失效` |
| 其他 | `success=false`，`凭证当前不可签到` |

说明：`ALREADY_CHECKED_IN` 是业务层“结果状态”，但接口层仍为 `success=true`，
便于前端展示“该凭证此前已完成签到”及原核销人/核销时间。

## 6. 成功核销 completeCheckIn

实现位于 [ActivityServiceImpl.java:1254](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1254)：

### 6.1 凭证核销（CAS 条件更新）

```sql
UPDATE tb_activity_voucher
SET status = 'CHECKED_IN',
    checked_in_time = now,
    checked_in_by = 操作人ID
WHERE id = ? AND status = 'UNUSED'
```

关键点：

- `WHERE id=? AND status='UNUSED'` 是“条件更新 + 状态机”防重，保证同一凭证只有一个请求能核销成功；
- 若影响行数为 0，重查凭证最新状态并走 `handleUnavailableVoucher`（通常是并发下另一请求已先核销，返回 `ALREADY_CHECKED_IN`）；
- 若重查不存在，返回 `INVALID_VOUCHER`。

### 6.2 更新报名记录

```sql
UPDATE tb_activity_registration
SET check_in_status = 1,   -- CHECKED_IN
    check_in_time  = now,
    voucher_id     = 凭证ID
WHERE id = 报名记录ID
```

### 6.3 核销后的联动

| 动作 | 说明 | 实现 |
| --- | --- | --- |
| 写审计记录 | 向 `tb_activity_check_in_record` 插入一条 `resultStatus=SUCCESS` 的记录，含 `requestKey/requestFingerprint/responseBody` | [recordCheckInAttempt:1360](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1360) |
| 失效签到缓存 | 删除该活动的 stats / dashboard / records 分页缓存 | [evictCheckInCache:3464](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:3464) |
| 刷新用户画像 | `embeddingTaskService.touchUser(userId, "CHECK_IN")`，写入用户画像 embedding 异步任务 | [EmbeddingTaskServiceImpl.java](../src/main/java/com/campus/service/impl/EmbeddingTaskServiceImpl.java:147) |
| 返回结果 | 组装 `ActivityCheckInResultDTO`，`resultStatus=SUCCESS`，`message=签到成功` | [buildCheckInResult:1344](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1344) |

## 7. 凭证生命周期与上游生成

### 7.1 状态机

```text
报名成功（审核通过 / 先到先得异步确认）
        │  ensureVoucherForRegistration
        ▼
     UNUSED ──核销成功──▶ CHECKED_IN（写核销人/核销时间）
        │
        ├─ 超时后首次尝试核销 ──▶ EXPIRED（expireVoucherIfNeeded）
        │
        └─ 退出/取消报名 ──▶ 物理删除凭证并清理展示码缓存

CANCELED：状态定义与处理分支已保留，但当前代码中未发现写入该状态的路径
```

### 7.2 凭证生成入口

| 场景 | 入口 | 说明 |
| --- | --- | --- |
| 审核制报名通过 | [approveRegistration:1906](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1906) | 审核通过 → `ensureVoucherForRegistration` |
| 先到先得异步确认 | RocketMQ 异步落库的 [saveRegistrationRecord:2955](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:2955) → [ensureVoucherForRegistration:3029](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:3029) | 报名落库成功后生成凭证 |
| 已成功报名补发 | `ensureVoucherForRegistration` 幂等 | 已有凭证则复用并回填 `registration.voucherId` |

凭证生成细节（[createVoucherForRegistration:3046](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:3046)）：

- ID 使用 `RedisIdWorker.nextId("activity-voucher")`（雪花算法）；
- 展示码 8 位，字符集 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`（去易混淆字符），数据库 `display_code` 唯一键兜底，冲突自动重试最多 10 次；
- 状态初始 `UNUSED`，同时把 `展示码 → voucherId` 写入 Redis（TTL 1 天）。

### 7.3 凭证失效/删除场景

| 场景 | 处理 | 位置 |
| --- | --- | --- |
| 已签到不可退出 | 校验拦截：`已签到记录不可退出` | [cancelRegistration:661](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:661) |
| 先到先得直接退出 | 删除凭证 + Redis 展示码映射 | [cancelDirectRegistration:1866](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1866) |
| 审核制退出申请通过 | 删除凭证 + Redis 展示码映射 | [approveCancelRegistration:1964](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1964) |
| 活动结束删除报名记录 | 删除核销记录 + 凭证 + Redis 映射 | [ActivityServiceImpl.java:811](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:811) |
| 超过签到窗口后首次核销 | `UNUSED → EXPIRED`（懒过期，无定时任务） | [expireVoucherIfNeeded:1430](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1430) |

## 8. 涉及数据表与 Redis Key

### 8.1 数据表

建表脚本：

- [db/migration/activity_checkin_voucher_upgrade.sql](../src/main/resources/db/migration/activity_checkin_voucher_upgrade.sql)
- [db/init/init_schema.sql](../src/main/resources/db/init/init_schema.sql:107)

| 表 | 实体 | 关键字段 |
| --- | --- | --- |
| `tb_activity_registration` | [ActivityRegistration.java](../src/main/java/com/campus/entity/ActivityRegistration.java) | `status`（1=SUCCESS）、`check_in_status`（0/1）、`check_in_time`、`voucher_id` |
| `tb_activity_voucher` | [ActivityVoucher.java](../src/main/java/com/campus/entity/ActivityVoucher.java) | `display_code`、`activity_id`、`registration_id`、`user_id`、`status`、`checked_in_time`、`checked_in_by`；唯一键：展示码、报名记录 |
| `tb_activity_check_in_record` | [ActivityCheckInRecord.java](../src/main/java/com/campus/entity/ActivityCheckInRecord.java) | `activity_id`、`voucher_id`、`user_id`、`operator_id`、`result_status`、`request_key`、`request_fingerprint`、`response_body` |

### 8.2 Redis Key

| Key 前缀 | TTL | 写入口 | 读入口 |
| --- | --- | --- | --- |
| `activity:voucher:display:{code}` | 1 天 | 凭证生成/查询后写入 | `findVoucher` 按展示码查凭证 |
| `activity:checkin:idempotency:{activityId}:{key}` | 10 分钟 | `verifyCheckIn` 抢锁与写结果 | `verifyCheckIn` 幂等返回 |
| `cache:activity:check-in:stats:{activityId}` | 5 分钟 | 查询时缓存 | `queryCheckInStats` |
| `cache:activity:check-in:dashboard:{activityId}` | 10 分钟 | 查询时缓存 | `queryCheckInDashboard` |
| `cache:activity:check-in:records:{activityId}:{page}:{size}` | 3 分钟 | 查询时缓存 | `queryCheckInRecords` |

核销成功后 `evictCheckInCache` 删除同一活动的 stats/dashboard 与全部 records 分页缓存，
下一次管理端查询会回源数据库重新加载。

## 9. 核销后数据从哪来（管理端查询）

| 管理接口 | 服务方法 | 数据来源 |
| --- | --- | --- |
| `GET /activity/manage/{id}/check-in/stats` | [queryCheckInStats:993](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:993) | `loadCheckInStats`：报名成功数 / 已核销凭证数 / 未签到数 / 签到率 |
| `GET /activity/manage/{id}/check-in/records` | [queryCheckInRecords:1014](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1014) | `tb_activity_check_in_record` 中 `result_status=SUCCESS` 的分页记录（缓存 + 用户昵称/操作人名称富化） |
| `GET /activity/manage/{id}/check-in/dashboard` | [queryCheckInDashboard:1028](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1028) | 活动摘要 + 统计 + 状态图 + 报名趋势 + 按小时签到趋势 + 最近 10 条成功记录 |

统计口径（[loadCheckInStats:1656](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1656)）：

- `registeredCount`：报名记录 `status=SUCCESS` 的数量；
- `checkedInCount`：凭证 `status=CHECKED_IN` 的数量；
- `uncheckedCount = max(registeredCount - checkedInCount, 0)`；
- `checkInRate = checkedInCount / registeredCount × 100%`。

## 10. 结果状态汇总

`resultStatus`（写入 `tb_activity_check_in_record`）取值：

| resultStatus | 含义 | 接口 success |
| --- | --- | --- |
| `SUCCESS` | 首次核销成功 | true |
| `ALREADY_CHECKED_IN` | 凭证此前已签到（重复核销） | true |
| `INVALID_VOUCHER` | 凭证不存在 / 已取消 / 已失效 / 不可用 | false |
| `ACTIVITY_MISMATCH` | 凭证不属于该活动 | false |
| `NOT_REGISTERED` | 报名记录不存在或状态非成功 | false |
| `OUT_OF_WINDOW` | 未到或已过签到时间窗口 | false |

每次核销尝试（含各类失败）都会写入一条审计记录，携带 `requestKey`、`requestFingerprint` 与完整响应摘要，支持问题回溯与统计。

## 11. 实现观察点

以下为阅读代码得到的客观事实，供后续优化参考：

1. **`@Transactional` 与类内自调用**：`completeCheckIn` 标注了 `@Transactional`
   （[1254](../src/main/java/com/campus/service/impl/ActivityServiceImpl.java:1254)），但它由
   `verifyCheckIn → doVerifyCheckIn` 在同类内部调用，默认 Spring 代理下注解不会生效，
   凭证更新与报名更新实际是两个独立提交；业务上的并发安全主要依赖
   `UPDATE ... WHERE id=? AND status='UNUSED'` 条件更新保证只成功一次。
2. **校验清单不含活动状态**：`verifyCheckIn` 不校验活动 `status` 是否仍为已发布/进行中，
   也不使用实体上的 `checkInEnabled`/`checkInCode`（这些字段当前未参与核销判定），时间窗口是主要约束。
3. **`CANCELED` 状态**：代码与表注释均保留 `CANCELED`，但当前业务逻辑中对“退出报名”采用物理删除凭证，
   未发现任何写 `CANCELED` 的路径。
4. **展示码只缓存 1 天**：凭证可能核销前跨天展示，若 Redis 映射过期则回源 MySQL，展示码唯一键保证查到的凭证准确。
5. **窗口常量硬编码**：提前 30 分钟开签、延后 30 分钟关签均为代码常量，未配置化。
