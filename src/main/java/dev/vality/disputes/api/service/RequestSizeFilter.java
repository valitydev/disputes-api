package dev.vality.disputes.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.swag.disputes.model.GeneralError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10 MB

    private final ObjectMapper customObjectMapper;

    @Override
    @SneakyThrows
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        if (request.getContentLengthLong() > MAX_REQUEST_SIZE) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String getMessage() throws JsonProcessingException {
        return customObjectMapper.writeValueAsString(new GeneralError()
                .message("Blocked: Request too large"));
    }
}
