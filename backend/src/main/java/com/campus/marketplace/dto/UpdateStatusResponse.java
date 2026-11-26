package com.campus.marketplace.dto;

public class UpdateStatusResponse {

    private Long id;
    private String status;

    public UpdateStatusResponse(Long id, String status) {
        this.id = id;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }
}
