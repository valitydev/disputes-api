package dev.vality.disputes.provider.payments.servlet;

import dev.vality.provider.payments.ProviderPaymentsAdminManagementServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/v1/provider-payments-admin-management")
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsAdminManagementServlet extends GenericServlet {

    @Autowired
    private ProviderPaymentsAdminManagementServiceSrv.Iface providerPaymentsAdminManagementHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder()
                .build(ProviderPaymentsAdminManagementServiceSrv.Iface.class, providerPaymentsAdminManagementHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}
