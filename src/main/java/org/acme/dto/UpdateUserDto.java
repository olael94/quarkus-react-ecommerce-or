package org.acme.dto;

import jakarta.validation.constraints.Email;

public class UpdateUserDto {
  private String username;

  @Email(message = "Email must be a valid email address")
  private String email;

  // Getters and setters
  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
