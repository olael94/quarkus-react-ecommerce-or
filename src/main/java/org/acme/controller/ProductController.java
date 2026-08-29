package org.acme.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import org.acme.dto.*;
import org.acme.entity.Product;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.util.SessionAuth;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductController {
  // The logger object is used to log messages to the console.
  private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

  @Inject S3Client s3Client;

  @Inject
  @ConfigProperty(name = "app.s3.product-images-bucket")
  String imagesBucket;

  @Inject
  @ConfigProperty(name = "quarkus.s3.aws.region")
  String awsRegion;

  private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

  // Create a new product
  @POST
  @Transactional
  @Operation(
      summary = "Create a product",
      description =
          "Requires VENDOR or ADMIN role. The owner is always set to the caller - it "
              + "cannot be set via the request body.")
  @APIResponse(responseCode = "201", description = "Product created")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks VENDOR or ADMIN role")
  public Response createProduct(
      @Valid CreateProductRequestDto request,
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {

    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.VENDOR, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    // Built field-by-field from the DTO, not bound directly from the request
    // body - a client has no way to set id or owner at creation.
    Product product = new Product();
    product.setProductName(request.getProductName());
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());
    product.setImageURL(request.getImageURL());
    product.setQuantity(request.getQuantity());
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
  @Operation(
      summary = "List all products",
      description = "Public - no authentication needed. Returns a lightweight summary per product.")
  @APIResponse(responseCode = "200", description = "All products returned")
  public List<ProductSummaryDto> getAllProducts() {
    logger.info("Fetching all products");
    List<Product> products = Product.listAll();
    return products.stream().map(ProductSummaryDto::new).collect(Collectors.toList());
  }

  // Get a product by ID
  @GET
  @Path("{id}")
  @Operation(summary = "Get a product by ID", description = "Public - no authentication needed.")
  @APIResponse(responseCode = "200", description = "Product found")
  @APIResponse(responseCode = "404", description = "No product with that ID")
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
  @Operation(
      summary = "Update a product",
      description = "Allowed for the product's owner, or a caller with ADMIN role.")
  @APIResponse(responseCode = "200", description = "Product updated")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session is neither the owner nor an admin")
  @APIResponse(responseCode = "404", description = "No product with that ID")
  public Response updateProduct(
      @PathParam("id") Long id,
      @Valid UpdateProductRequestDto request,
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {
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

    existingProduct.setProductName(request.getProductName());
    existingProduct.setDescription(request.getDescription());
    existingProduct.setPrice(request.getPrice());
    existingProduct.setImageURL(request.getImageURL());
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

  // Upload or replace a product's image
  @POST
  @Path("{id}/image")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Transactional
  @Operation(
      summary = "Upload a product image",
      description =
          "Allowed for the product's owner, or a caller with ADMIN role. Replaces the "
              + "product's existing image, if any. Accepts JPEG, PNG, or "
              + "WEBP, up to 5 MB")
  @APIResponse(responseCode = "200", description = "Image uploaded, imageURL updated")
  @APIResponse(responseCode = "400", description = "File missing, not an image, or too large")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session is neither the owner nor an admin")
  @APIResponse(responseCode = "404", description = "No product with that ID")
  public Response uploadProductImage(
      @PathParam("id") Long id,
      @RestForm("image") FileUpload image,
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Product product = Product.findById(id);
    if (product == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Product not found"))
          .build();
    }

    boolean isOwner = product.getOwner() != null && product.getOwner().id.equals(session.user.id);
    boolean isAdmin = session.user.hasRole(User.Role.ADMIN);
    if (!isOwner && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // VALIDATE THE FILE

    // Check if the file is present
    if (image == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("No image file provided"))
          .build();
    }

    String extension =
        switch (image.contentType()) {
          case "image/jpeg" -> "jpg";
          case "image/png" -> "png";
          case "image/webp" -> "webp";
          default -> null;
        };

    // Check if the file is an image with a valid extension
    if (extension == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Image must be JPEG, PNG, or WEBP"))
          .build();
    }

    // Check if the file is not too large
    if (image.size() > MAX_IMAGE_SIZE_BYTES) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Image must be less than 5 MB"))
          .build();
    }

    // UPLOAD to S3, UPDATE THE PRODUCT
    String key = "products/" + id + "." + extension;
    String url = "https://" + imagesBucket + ".s3." + awsRegion + ".amazonaws.com/" + key;

    // Upload the image to S3
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(imagesBucket)
            .key(key)
            .contentType(image.contentType())
            .build(),
        RequestBody.fromFile(image.filePath()));
    product.setImageURL(url);
    product.persist();

    logger.info("Uploaded image for product {}: {}", id, url);
    return Response.ok(new ProductDto(product)).build();
  }

  // Delete a product by ID
  @DELETE
  @Path("{id}")
  @Transactional
  @Operation(
      summary = "Delete a product",
      description = "Allowed for the product's owner, or a caller with ADMIN role.")
  @APIResponse(responseCode = "200", description = "Product deleted")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session is neither the owner nor an admin")
  @APIResponse(responseCode = "404", description = "No product with that ID")
  public Response deleteProduct(
      @PathParam("id") Long id,
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {
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
