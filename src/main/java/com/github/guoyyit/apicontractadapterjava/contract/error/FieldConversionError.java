package com.github.guoyyit.apicontractadapterjava.contract.error;

/**
 * 结构化字段转换错误：明确给出字段路径与失败原因。
 *
 * @param path   出错字段的路径
 * @param reason 失败原因
 */
public record FieldConversionError(String path, String reason) {

    @Override
    public String toString() {
        return path + ": " + reason;
    }
}
