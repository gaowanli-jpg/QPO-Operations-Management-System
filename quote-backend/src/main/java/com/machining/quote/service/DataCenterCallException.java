package com.machining.quote.service;

public class DataCenterCallException extends RuntimeException {
    private final String endpointCode;
    private final FailureCertainty certainty;
    private final Integer httpStatus;

    public DataCenterCallException(String endpointCode,
                                   FailureCertainty certainty,
                                   Integer httpStatus,
                                   String message,
                                   Throwable cause) {
        super(message, cause);
        this.endpointCode = endpointCode;
        this.certainty = certainty;
        this.httpStatus = httpStatus;
    }

    public String getEndpointCode() {
        return endpointCode;
    }

    public FailureCertainty getCertainty() {
        return certainty;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public enum FailureCertainty {
        DEFINITE_FAILURE,
        RESULT_UNKNOWN
    }
}
