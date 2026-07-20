package com.validdoc.dto.response;

public class RuleTypeResponse {

    private final String type;
    private final boolean requiresParam;
    private final boolean inkRule;

    public RuleTypeResponse(String type, boolean requiresParam, boolean inkRule) {
        this.type = type;
        this.requiresParam = requiresParam;
        this.inkRule = inkRule;
    }

    public String getType() { return type; }
    public boolean isRequiresParam() { return requiresParam; }
    public boolean isInkRule() { return inkRule; }
}