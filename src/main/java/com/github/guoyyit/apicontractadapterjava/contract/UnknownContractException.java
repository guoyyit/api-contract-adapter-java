package com.github.guoyyit.apicontractadapterjava.contract;

public class UnknownContractException extends RuntimeException {

    private final String clientId;

    public UnknownContractException(String clientId) {
        super("no contract bound to client: " + clientId);
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }
}
