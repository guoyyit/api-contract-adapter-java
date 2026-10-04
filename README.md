# API Contract Adapter

对外接口的字段契约适配层。不同客户端绑定不同的字段契约（contract），所有契约同时映射到同一内部资源模型；
请求（入站）与响应（出站）按客户端绑定的契约做双向转换，接口演进时老客户端无需同步升级。

## 快速开始

```bash
mvn spring-boot:run
```

加载契约（整体热更新，原子生效）：

```bash
curl -X PUT localhost:8080/admin/contracts -H 'Content-Type: application/json' -d '{
  "contracts": {
    "v1": {
      "inboundPolicy": "REJECT",
      "outboundPolicy": "HIDE",
      "rules": [
        {"source": "userName", "target": "name"},
        {"source": "age", "target": "age", "conversion": "TO_INTEGER"}
      ]
    },
    "v2": {
      "rules": [
        {"source": "fullName", "target": "name"}
      ]
    }
  },
  "bindings": {"client-a": "v1", "client-b": "v2"}
}'
```

客户端按自己的契约读写同一资源：

```bash
curl -X PUT localhost:8080/api/clients/client-a/resources/r1 \
  -H 'Content-Type: application/json' \
  -d '{"userName": "alice", "age": "30"}'
# => {"userName":"alice","age":30}

curl localhost:8080/api/clients/client-b/resources/r1
# => {"fullName":"alice"}   （v2 未声明 age，按 HIDE 策略不暴露）
```

## 配置格式

`PUT /admin/contracts` 接收一份完整配置，**整体校验、整体生效**：

| 字段 | 说明 |
| --- | --- |
| `contracts.<id>.rules[]` | 字段映射规则，按声明顺序应用 |
| `contracts.<id>.inboundPolicy` | 未声明入站字段策略：`REJECT`（默认，报结构化错误）/ `IGNORE` |
| `contracts.<id>.outboundPolicy` | 未声明内部字段出站策略：`HIDE`（默认）/ `EXPOSE` |
| `bindings.<clientId>` | 客户端到契约的绑定 |

规则字段：

| 字段 | 说明 |
| --- | --- |
| `source` | 客户端文档中的字段路径（点分隔，如 `address.city`） |
| `target` | 内部模型中的字段路径 |
| `conversion` | 可选类型转换：`NONE`（默认）、`TO_STRING`、`TO_INTEGER`、`TO_LONG`、`TO_DOUBLE`、`TO_BOOLEAN` |
| `required` | 缺省 `true`。源字段缺失且无默认值时产生 `FIELD_MISSING` 错误；`false` 时跳过 |
| `defaultValue` | 源字段**缺失**时填充的默认值（填充时同样应用 `conversion`） |
| `children` | 嵌套规则。源值为对象时递归映射；为数组时逐元素映射（保持顺序、不增删元素） |

## 映射语义

- **双向同一规则集**：入站按 `source -> target`，出站按 `target -> source`，无需维护两份配置。
- **缺失与空值区分**：字段缺失触发 `defaultValue` / `required` 逻辑；显式 `null` 是已存在的值，原样透传（转换对 `null` 恒等）。
- **严格类型转换**：转换不会静默截断或猜测（如 `"4x"`、`4.5` 转整数、越界 long 转 int 均失败）。
- **结构化错误**：入站任何无法映射或转换失败的字段都汇入错误清单，请求整体失败（HTTP 422），
  不写入部分结果或错误值：

  ```json
  {"errors": [{"path": "items[2].price", "code": "CONVERSION_FAILED", "message": "cannot convert value \"abc\" (String) to DOUBLE"}]}
  ```

  错误码：`FIELD_MISSING`、`CONVERSION_FAILED`、`UNDECLARED_FIELD`、`TYPE_MISMATCH`。
- **确定性**：同一数据 + 同一规则快照，多次转换结果完全一致（规则按声明顺序应用，未声明字段按字典序处理）。
- **嵌套与集合**：`children` 递归映射嵌套对象；源值为数组时对每个元素应用子规则，保持元素顺序，不引入重复元素；`null` 元素原样保留。

## 一致性与热更新保证

- **原子热更新**：契约保存在不可变快照中，通过 `AtomicReference` 整体切换。在途请求在请求开始时获取一次快照，
  整个请求（入站 + 出站）要么完整使用旧规则，要么完整使用新规则，绝不混合。
- **非法规则整体拒绝**：新配置先解析再校验，所有错误（语法 + 语义，如重复路径、非法转换、悬空绑定、
  不可转换的默认值）一次性收齐返回（HTTP 400），当前生效规则不受任何影响。
- **一致的资源状态**：资源以不可变快照存储，写操作通过 `ConcurrentHashMap.compute` 原子地产生新版本（版本号单调递增）。
  任一客户端任意时刻读到的都是某一个完整资源状态的契约视图，不同契约之间不会出现互相矛盾的读取结果。

## 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `PUT` | `/admin/contracts` | 整体热更新契约配置；非法配置返回 400 + 完整错误清单 |
| `GET` | `/admin/contracts` | 查看当前生效配置 |
| `PUT` | `/api/clients/{clientId}/resources/{id}` | 按客户端契约入站转换并保存，返回出站视图 |
| `GET` | `/api/clients/{clientId}/resources/{id}` | 按客户端契约出站转换读取 |

## 测试

```bash
mvn test
```

覆盖：嵌套/集合递归映射、类型转换失败、缺失与空值区分、未声明字段策略、转换确定性、
热更新原子性与并发一致性、非法规则整体拒绝、多客户端多契约端到端读写。
