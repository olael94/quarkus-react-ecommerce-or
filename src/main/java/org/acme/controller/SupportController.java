package org.acme.controller;

import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.acme.dto.OrderDto;
import org.acme.entity.Order;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.util.SessionAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/support")
@Produces(MediaType.APPLICATION_JSON)
public class SupportController {

  private static final Logger logger = LoggerFactory.getLogger(SupportController.class);

  // Get all orders (support or admin only)
  @GET
  @Path("/orders")
  public Response getAllOrders(@CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Support and admin can view all orders
    Response forbidden = SessionAuth.requireRole(session, User.Role.SUPPORT, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    logger.info("Fetching all orders");
    List<Order> orders = Order.listAll();
    List<OrderDto> orderDtos = new ArrayList<>();
    for (Order order : orders) {
      orderDtos.add(new OrderDto(order));
    }

    return Response.ok(orderDtos).build();
  }
}
