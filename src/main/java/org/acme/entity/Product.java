package org.acme.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "Product")
public class Product extends PanacheEntity {
  @JsonProperty("productName")
  @Column(nullable = false)
  private String productName;

  @Column(nullable = false, length = 2000)
  private String description;

  @Column(nullable = false)
  private Double price;

  @Column(nullable = false)
  private String imageURL;

  @Column(nullable = false)
  private Integer quantity;

  @ManyToOne(optional = true)
  @JoinColumn(name = "fk_owner_id")
  private User owner;

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

  public User getOwner() {
    return owner;
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

  public void setOwner(User owner) {
    this.owner = owner;
  }

  // The toString method is used to convert the object to a string representation.
  @Override
  public String toString() {
    return productName + " " + description + " " + price + " " + imageURL;
  }
}
