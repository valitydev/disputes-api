package dev.vality.disputes.servlet;

import dev.vality.disputes.admin.ManualParsingServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/disputes-api/manual-parsing")
public class ManualParsingServlet extends GenericServlet {

    @Autowired
    private ManualParsingServiceSrv.Iface manualParsingHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder()
                .build(ManualParsingServiceSrv.Iface.class, manualParsingHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}
