package dev.vality.disputes.config;

import dev.vality.woody.api.flow.WFlow;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class NetworkConfig {

    @Value("${server.port}")
    private int restPort;

    @Value("${openapi.valityDisputes.base-path:/disputes}/")
    private String restEndpoint;

    public static final String HEALTH = "/actuator/health";
    public static final String MERCHANT = "/v1/merchant";
    public static final String ADMIN_MANAGEMENT = "/v1/admin-management";
    public static final String CALLBACK = "/v1/callback";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> externalPortRestrictingFilter() {
        var filter = new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                var servletPath = request.getServletPath();
                var enabledPaths = servletPath.startsWith(restEndpoint)
                        || servletPath.startsWith(HEALTH)
                        || servletPath.startsWith(MERCHANT)
                        || servletPath.startsWith(ADMIN_MANAGEMENT)
                        || servletPath.startsWith(CALLBACK);
                if ((request.getLocalPort() == restPort) && !enabledPaths) {
                    response.sendError(404, "Unknown address");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
        var filterRegistrationBean = new FilterRegistrationBean<OncePerRequestFilter>();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.setOrder(-100);
        filterRegistrationBean.setName("httpPortFilter");
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> woodyFilter() {
        var woodyFlow = new WFlow();
        var filter = new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if ((request.getLocalPort() == restPort)
                        && request.getServletPath().startsWith(restEndpoint)) {
                    woodyFlow.createServiceFork(() -> doFilter(request, response, filterChain)).run();
                    return;
                }
                doFilter(request, response, filterChain);
            }

            @SneakyThrows
            private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
                filterChain.doFilter(request, response);
            }
        };
        var filterRegistrationBean = new FilterRegistrationBean<OncePerRequestFilter>();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.setOrder(-50);
        filterRegistrationBean.setName("woodyFilter");
        filterRegistrationBean.addUrlPatterns(restEndpoint + "*");
        return filterRegistrationBean;
    }
}
