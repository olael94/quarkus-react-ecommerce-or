package org.acme.exception;

import jakarta.ws.rs.core.Response;
import org.acme.dto.MessageDto;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class RateLimitExceptionMapper {

  @ServerExceptionMapper
  public Response mapRateLimitExceeded(RateLimitExceededException exception) {
    return Response.status(429).entity(new MessageDto(exception.getMessage())).build();
  }
}
