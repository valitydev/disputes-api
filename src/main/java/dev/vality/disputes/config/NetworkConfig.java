package dev.vality.disputes.config;

import dev.vality.woody.api.flow.WFlow;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public static final String PAYMENTS_ADMIN_MANAGEMENT = "/v1/payments-admin-management";

    @Bean
    public FilterRegistrationBean externalPortRestrictingFilter() {
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
                        || servletPath.startsWith(CALLBACK)
                        || servletPath.startsWith(PAYMENTS_ADMIN_MANAGEMENT);
                if ((request.getLocalPort() == restPort) && !enabledPaths) {
                    response.sendError(404, "Unknown address");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
        var filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.setOrder(-100);
        filterRegistrationBean.setName("httpPortFilter");
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }

    @Bean
    @SuppressWarnings("LocalVariableName")
    public FilterRegistrationBean woodyFilter() {
        var wFlow = new WFlow();
        var filter = new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if ((request.getLocalPort() == restPort)
                        && request.getServletPath().startsWith(restEndpoint)) {
                    wFlow.createServiceFork(() -> {
                        try {
                            filterChain.doFilter(request, response);
                        } catch (IOException | ServletException e) {
                            sneakyThrow(e);
                        }
                    }).run();
                    return;
                }
                filterChain.doFilter(request, response);
            }

            private <E extends Throwable, T> T sneakyThrow(Throwable t) throws E {
                throw (E) t;
            }
        };
        var filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.setOrder(-50);
        filterRegistrationBean.setName("woodyFilter");
        filterRegistrationBean.addUrlPatterns(restEndpoint + "*");
        return filterRegistrationBean;
    }
}
