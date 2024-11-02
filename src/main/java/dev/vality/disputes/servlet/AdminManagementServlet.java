package dev.vality.disputes.servlet;

import dev.vality.disputes.admin.AdminManagementServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/v1/admin-management")
public class AdminManagementServlet extends GenericServlet {

    @Autowired
    private AdminManagementServiceSrv.Iface adminManagementHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder()
                .build(AdminManagementServiceSrv.Iface.class, adminManagementHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}
