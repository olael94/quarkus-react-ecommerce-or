package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "OrderItem")
public class OrderItem extends PanacheEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "fk_order_id")
  private Order order;

  @ManyToOne(optional = false)
  @JoinColumn(name = "fk_product_id")
  private Product product;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false)
  private Double unitPrice;

  // Getters
  public Order getOrder() {
    return order;
  }

  public Product getProduct() {
    return product;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public Double getUnitPrice() {
    return unitPrice;
  }

  // Setters
  public void setOrder(Order order) {
    this.order = order;
  }

  public void setProduct(Product product) {
    this.product = product;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public void setUnitPrice(Double unitPrice) {
    this.unitPrice = unitPrice;
  }

  @Override
  public String toString() {
    return "OrderItem{"
        + "order="
        + order
        + ", product="
        + product
        + ", quantity="
        + quantity
        + ", unitPrice="
        + unitPrice
        + '}';
  }
}
