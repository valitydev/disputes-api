package dev.vality.disputes.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.swag.disputes.model.GeneralError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeFilter extends OncePerRequestFilter {

    @Value("${dispute.maxRequestSize}")
    private long maxRequestSize;

    private final ObjectMapper customObjectMapper;

    @Override
    @SneakyThrows
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        if (request.getContentLengthLong() > maxRequestSize * 1024 * 1024) {
            log.warn("413 SC_REQUEST_ENTITY_TOO_LARGE: invalid request {}", getRequestInfo(request));
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String getMessage() throws JsonProcessingException {
        return customObjectMapper.writeValueAsString(new GeneralError()
                .message("Blocked: Request too large"));
    }

    private String getRequestInfo(HttpServletRequest request) {
        var info = new StringBuilder();
        info.append("method=").append(request.getMethod())
                .append(", url=").append(request.getRequestURL())
                .append(", uri=").append(request.getRequestURI())
                .append(", query=").append(request.getQueryString())
                .append(", contentLength=").append(request.getContentLengthLong())
                .append(", contentType=").append(request.getContentType());

        info.append(", headers=[");
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            info.append(headerName).append("=").append(request.getHeader(headerName)).append("; ");
        }
        info.append("]");

        return info.toString();
    }
}
