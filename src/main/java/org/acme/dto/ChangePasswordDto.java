package org.acme.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordDto {
  @NotBlank(message = "Current password is required")
  private String currentPassword;

  @NotBlank(message = "New password is required")
  @Size(min = 8, message = "New password must be at least 8 characters")
  private String newPassword;

  // Getters and setters
  public String getCurrentPassword() {
    return currentPassword;
  }

  public String getNewPassword() {
    return newPassword;
  }

  public void setCurrentPassword(String currentPassword) {
    this.currentPassword = currentPassword;
  }

  public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
  }
}
