package dev.vality.disputes.servlet;

import dev.vality.disputes.callback.ProviderPaymentsCallbackServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/v1/callback")
public class ProviderPaymentsCallbackServlet extends GenericServlet {

    @Autowired
    private ProviderPaymentsCallbackServiceSrv.Iface providerPaymentsCallbackHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder()
                .build(ProviderPaymentsCallbackServiceSrv.Iface.class, providerPaymentsCallbackHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}
