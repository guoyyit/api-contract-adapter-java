package com.github.guoyyit.apicontractadapterjava.contract.model;

/**
 * 单条声明式字段映射规则。
 *
 * @param sourcePath   客户端契约中的字段路径（点分嵌套路径）
 * @param targetPath   内部资源模型中的字段路径
 * @param type         目标类型名（可选，null 表示不转换）
 * @param defaultValue 缺失时填充的默认值（可选）
 * @param required     是否必填，必填缺失且无默认值时报错
 */
public record FieldRule(String sourcePath,
                        String targetPath,
                        String type,
                        Object defaultValue,
                        boolean required) {
}
