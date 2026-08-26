package org.acme.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.acme.dto.MessageDto;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Custom exception mapper for handling ConstraintViolationException */
public class ValidationExceptionMapper {

  @ServerExceptionMapper
  public Response mapConstraintViolation(ConstraintViolationException exception) {
    String message =
        exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining(", "));
    return Response.status(Response.Status.BAD_REQUEST).entity(new MessageDto(message)).build();
  }
}
