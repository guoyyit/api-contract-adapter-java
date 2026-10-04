package com.github.guoyyit.apicontractadapterjava.contract.model;

/**
 * 出站转换时对未在规则集中声明的字段的暴露策略。
 */
public enum UndeclaredFieldPolicy {
    /** 原样暴露未声明字段。 */
    EXPOSE,
    /** 省略未声明字段。 */
    OMIT,
    /** 遇到未声明字段即报错。 */
    FAIL
}
