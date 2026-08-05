package com.validdoc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminPasswordResetRequest {

    @NotBlank
    private String adminPassword;

    @NotBlank
    @Size(min = 8, message = "{error.password.min_size}")
    private String newPassword;

    public AdminPasswordResetRequest() {}

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}