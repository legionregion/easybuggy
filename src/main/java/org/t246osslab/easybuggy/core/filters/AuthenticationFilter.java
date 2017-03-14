package org.t246osslab.easybuggy.core.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import sun.util.logging.resources.logging;

/**
 * Servlet Filter for authentication
 */
@WebFilter(urlPatterns = { "/*" })
public class AuthenticationFilter implements Filter {

    /**
     * Default constructor.
     */
    public AuthenticationFilter() {
    }

    /**
     * Call {@link #doFilter(HttpServletRequest, HttpServletResponse, FilterChain)}.
     * 
     * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException,
            ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String target = request.getRequestURI();
        
        if (target.startsWith("/admins") || target.equals("/udc/serverinfo.jsp")) {
            /* Login (authentication) is needed to access admin pages (under /admins). */
            
            String loginType = request.getParameter("logintype");
            String queryString = request.getQueryString();
            if (queryString == null) {
                queryString = "";
            } else {
                /* Remove "logintype" parameter from query string.
                    (* "logintype" specifies a login servlet) */
                queryString = queryString.replace("logintype=" + loginType + "&", "");
                queryString = queryString.replace("&logintype=" + loginType, "");
                queryString = queryString.replace("logintype=" + loginType, "");
                if (queryString.length() > 0) {
                    queryString = "?" + queryString;
                }
            }
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("authNResult") == null
                    || !"authenticated".equals(session.getAttribute("authNResult"))) {
                /* Not authenticated yet */
                session = request.getSession(true);
                session.setAttribute("target", target);
                if (loginType == null) {
                    response.sendRedirect("/login" + queryString);
                } else {
                    response.sendRedirect("/" + loginType + "/login" + queryString);
                }
            }
        }
        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
    }
}