# API Contract Adapter

对外接口的字段契约适配层。接口演进时不同客户端依赖不同字段结构，适配层让所有客户端契约同时映射到同一内部资源模型：请求与响应按客户端绑定的契约做双向转换，调用方无需强制同步升级。

## 核心概念

- **内部资源模型**：服务端的统一数据表示（嵌套 Map 结构），由 `ResourceStore` 并发安全地持有，读取返回一致性快照。
- **契约（contract）**：一个客户端绑定的一组字段映射规则（`ContractRuleSet`），不同客户端可绑定不同契约。
- **规则集热更新**：规则集不可变，整体校验通过后原子替换；在途请求要么完整使用旧规则，要么完整使用新规则，不会混合转换。非法规则在加载前被整体拒绝，返回完整错误清单，且不影响正在生效的规则。

## 字段映射规则

每条 `FieldRule` 声明一个字段的双向映射：

| 属性 | 含义 |
| --- | --- |
| `sourcePath` | 客户端契约中的字段路径（点分嵌套） |
| `targetPath` | 内部资源模型中的字段路径 |
| `type` | 目标类型（可选）：`string` / `integer` / `long` / `double` / `boolean` |
| `defaultValue` | 字段缺失时填充的默认值（可选） |
| `required` | 是否必填；必填缺失且无默认值时产生结构化错误 |

路径支持：

- **字段重命名**：`user_name -> profile.name`
- **路径迁移**：点分段描述嵌套对象，如 `address.zip -> location.postal_code`
- **集合递归映射**：段后缀 `[]` 表示对集合每个元素递归应用剩余路径，如 `items[].price -> line_items[].unit_price`；转换保持元素顺序，不引入重复元素

## 映射语义

### 入站（客户端视图 -> 内部模型）

- 按规则逐字段提取、转换、写入；未声明的客户端字段被忽略。
- **缺失与空值区分**：路径不存在视为缺失——有默认值则填默认值，必填则报错，否则跳过；显式 `null` 是空值，原样保留，不触发默认值与类型转换。
- 任何无法映射（路径结构与规则不符）或类型转换失败的字段都产生结构化错误 `{path, reason}`，收集完整清单后整体失败（HTTP 422），不静默丢弃、不写入错误值。

### 出站（内部模型 -> 客户端视图）

- 使用同一规则集反向映射，类型转换与默认值语义与入站一致。
- 未声明字段由规则集的 `undeclaredFieldPolicy` 控制：
  - `OMIT`（默认）：不暴露；
  - `EXPOSE`：原样透传（不覆盖规则已声明的输出）；
  - `FAIL`：遇到未声明字段即报错。
- 转换是纯函数：同一数据在同一请求内多次转换结果完全一致。

### 一致性

- `ResourceStore` 读写均做深拷贝，任一客户端读到的快照对应某一时刻完整的资源状态；不同契约只是同一内部状态的不同投影，不会互相矛盾。

## 配置格式（热更新）

`PUT /api/contracts/{contractId}/rules`

```json
{
  "version": 2,
  "undeclaredFieldPolicy": "OMIT",
  "rules": [
    {"sourcePath": "user_name", "targetPath": "profile.name",
     "type": null, "defaultValue": null, "required": true},
    {"sourcePath": "items[].price", "targetPath": "line_items[].unit_price",
     "type": "double", "defaultValue": null, "required": false},
    {"sourcePath": "age", "targetPath": "metrics.age",
     "type": "integer", "defaultValue": 0, "required": false}
  ]
}
```

加载前整体校验：路径空白或含空段、类型不受支持、`sourcePath`/`targetPath` 重复等都会使整批规则被拒绝（HTTP 400），响应携带完整错误清单，当前生效规则不受影响。

`GET /api/contracts/{contractId}/rules` 查询当前生效规则集。

## 资源接口

- `PUT /api/resources/{id}?contract={contractId}`：按契约入站转换后写入内部模型，响应为同契约的出站视图。
- `GET /api/resources/{id}?contract={contractId}`：读取一致性快照并按契约出站转换。

## 构建与测试

```bash
mvn compile   # 编译
mvn test      # 运行全部测试（嵌套映射、类型转换失败、缺失与空值、热更新并发、规则校验拒绝、Web 集成）
```
