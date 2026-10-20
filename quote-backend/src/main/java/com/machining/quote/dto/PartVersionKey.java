package com.machining.quote.dto;

public record PartVersionKey(String partNo, String version) {
    public String getPartNo() {
        return partNo;
    }

    public String getVersion() {
        return version;
    }
}
