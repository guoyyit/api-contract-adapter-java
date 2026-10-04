package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.ArrayList;
import java.util.List;

public class FieldRule {

    private String source;
    private String target;
    private TypeConversion conversion = TypeConversion.NONE;
    private boolean required = true;
    private boolean hasDefault;
    private Object defaultValue;
    private List<FieldRule> children = new ArrayList<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public TypeConversion getConversion() {
        return conversion;
    }

    public void setConversion(TypeConversion conversion) {
        this.conversion = conversion;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean hasDefault() {
        return hasDefault;
    }

    public void setHasDefault(boolean hasDefault) {
        this.hasDefault = hasDefault;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<FieldRule> getChildren() {
        return children;
    }

    public void setChildren(List<FieldRule> children) {
        this.children = children;
    }
}
