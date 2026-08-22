package org.acme.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import org.acme.dto.MessageDto;
import org.acme.dto.ProductDto;
import org.acme.dto.ProductSummaryDto;
import org.acme.entity.Product;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.util.SessionAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductController {
  // The logger object is used to log messages to the console.
  private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

  // Create a new product
  @POST
  @Transactional
  public Response createProduct(Product product, @CookieParam("session") Cookie sessionCookie) {

    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.VENDOR, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    product.setOwner(session.user);
    logger.info("Creating product: {}", product.getProductName());
    product.persist(); // Persist the product

    // User will see this message
    String message = "Product " + product.getProductName() + " created successfully";
    return Response.status(Response.Status.CREATED).entity(new MessageDto(message)).build();
  }

  // Get all products in the database.
  // Returns a lightweight summary (no description) to keep list responses small.
  @GET
  public List<ProductSummaryDto> getAllProducts() {
    logger.info("Fetching all products");
    List<Product> products = Product.listAll();
    return products.stream().map(ProductSummaryDto::new).collect(Collectors.toList());
  }

  // Get a product by ID
  @GET
  @Path("{id}")
  public Response getProduct(@PathParam("id") Long id) {
    Product product = Product.findById(id);
    if (product == null) {
      logger.error("Product with ID {} not found", id);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Product not found")) // User will see this message
          .build();
    }
    logger.info("Fetching product with ID {}", id);
    return Response.ok(new ProductDto(product)).build();
  }

  // Update a product by ID
  @PUT
  @Path("{id}")
  @Transactional
  public Response updateProduct(
      @PathParam("id") Long id, Product product, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Product existingProduct = Product.findById(id);
    if (existingProduct == null) {
      logger.error("Product with ID {} not found for update", id);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Product not found")) // User will see this message
          .build();
    }

    // Check if the user is the owner or an admin
    boolean isOwner =
        existingProduct.getOwner() != null && existingProduct.getOwner().id.equals(session.user.id);
    boolean isAdmin = session.user.hasRole(User.Role.ADMIN);
    if (!isOwner && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    existingProduct.setProductName(product.getProductName());
    existingProduct.setDescription(product.getDescription());
    existingProduct.setPrice(product.getPrice());
    existingProduct.setImageURL(product.getImageURL());
    existingProduct.persist();

    logger.info("Updated product with ID {}", id);
    // User will see this message
    String message =
        "Product "
            + existingProduct.getProductName()
            + " with ID: "
            + existingProduct.id
            + " updated successfully";
    return Response.ok(new MessageDto(message)).build();
  }

  // Delete a product by ID
  @DELETE
  @Path("{id}")
  @Transactional
  public Response deleteProduct(
      @PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Product product = Product.findById(id);
    if (product == null) {
      logger.error("Product with ID {} not found for deletion", id);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Product not found")) // User will see this message
          .build();
    }

    boolean isOwner = product.getOwner() != null && product.getOwner().id.equals(session.user.id);
    boolean isAdmin = session.user.hasRole(User.Role.ADMIN);
    if (!isOwner && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    product.delete();
    logger.info("Deleted product with ID {}", id);
    // User will see this message
    String message =
        "Product " + product.getProductName() + " with ID: " + product.id + " deleted successfully";
    return Response.ok(new MessageDto(message)).build();
  }
}
