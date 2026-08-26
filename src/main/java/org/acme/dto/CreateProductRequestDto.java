package org.acme.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public class CreateProductRequestDto {
  @NotBlank(message = "Product name is required")
  private String productName;

  @NotBlank(message = "Description is required")
  @Size(max = 2000, message = "Description must be at most 2000 characters")
  private String description;

  @NotNull(message = "Price is required")
  @PositiveOrZero(message = "Price must be a positive or zero number")
  private Double price;

  @NotBlank(message = "Image URL is required")
  @URL(message = "Image URL must be a valid URL")
  private String imageURL;

  @NotNull(message = "Quantity is required")
  @PositiveOrZero(message = "Quantity must be a positive or zero number")
  private Integer quantity;

  // Getters
  public String getProductName() {
    return productName;
  }

  public String getDescription() {
    return description;
  }

  public Double getPrice() {
    return price;
  }

  public String getImageURL() {
    return imageURL;
  }

  public Integer getQuantity() {
    return quantity;
  }

  // Setters
  public void setProductName(String productName) {
    this.productName = productName;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  public void setImageURL(String imageURL) {
    this.imageURL = imageURL;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }
}
