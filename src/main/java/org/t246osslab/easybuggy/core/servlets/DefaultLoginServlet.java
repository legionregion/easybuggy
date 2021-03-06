package org.t246osslab.easybuggy.core.servlets;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.owasp.esapi.ESAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.t246osslab.easybuggy.core.dao.EmbeddedADS;
import org.t246osslab.easybuggy.core.model.User;
import org.t246osslab.easybuggy.core.utils.ApplicationUtils;
import org.t246osslab.easybuggy.core.utils.HTTPResponseCreator;
import org.t246osslab.easybuggy.core.utils.MessageUtils;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/login" })
public class DefaultLoginServlet extends HttpServlet {
    
    /* User's login history using in-memory account locking */
    protected ConcurrentHashMap<String, User> userLoginHistory = new ConcurrentHashMap<String, User>();
    
    private static final Logger log = LoggerFactory.getLogger(DefaultLoginServlet.class);

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {

        Locale locale = req.getLocale();
        StringBuilder bodyHtml = new StringBuilder();

        bodyHtml.append("<p>" + MessageUtils.getMsg("msg.need.admin.privilege", locale) + " ");
        bodyHtml.append(MessageUtils.getMsg("msg.enter.id.and.password", locale) + "</p>");
        bodyHtml.append("<form method=\"POST\" action=\"" + req.getRequestURI() + "\">");
        bodyHtml.append("<table width=\"400px\" height=\"150px\">");
        bodyHtml.append("<tr>");
        bodyHtml.append("<td>" + MessageUtils.getMsg("label.user.id", locale) + " :&nbsp;</td>");
        bodyHtml.append("<td><input type=\"text\" name=\"userid\" size=\"20\"></td>");
        bodyHtml.append("</tr>");
        bodyHtml.append("<tr>");
        bodyHtml.append("<td>" + MessageUtils.getMsg("label.password", locale) + " :&nbsp;</td>");
        bodyHtml.append("<td><input type=\"password\" name=\"password\" size=\"20\" autocomplete=\"off\"></td>");
        bodyHtml.append("</tr>");
        bodyHtml.append("<tr>");
        bodyHtml.append("<td></td>");
        bodyHtml.append("<td><input type=\"submit\" value=\"" + MessageUtils.getMsg("label.login", locale) + "\"></td>");
        bodyHtml.append("</tr>");
        bodyHtml.append("</table>");
        String queryString = req.getQueryString();
        if (queryString != null) {
            bodyHtml.append("<input type=\"hidden\" name=\"loginquerystring\" value=\""
                    + ESAPI.encoder().encodeForHTML(queryString) + "\">");
        }
        Enumeration<?> paramNames = req.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = (String) paramNames.nextElement();
            String[] paramValues = req.getParameterValues(paramName);
            for (int i = 0; i < paramValues.length; i++) {
                bodyHtml.append("<input type=\"hidden\" name=\"" + ESAPI.encoder().encodeForHTML(paramName)
                        + "\" value=\"" + ESAPI.encoder().encodeForHTML(paramValues[i]) + "\">");
            }
        }

        HttpSession session = req.getSession(true);
        if (session.getAttribute("authNMsg") != null && !"authenticated".equals(session.getAttribute("authNMsg"))) {
            bodyHtml.append(MessageUtils.getErrMsg((String)session.getAttribute("authNMsg"), locale));
            session.setAttribute("authNMsg", null);
        }
        if (req.getAttribute("login.page.note") != null) {
            bodyHtml.append(MessageUtils.getInfoMsg((String) req.getAttribute("login.page.note"), locale));
        }
        bodyHtml.append("</form>");
        HTTPResponseCreator.createSimpleResponse(req, res, MessageUtils.getMsg("title.login.page", locale),
                bodyHtml.toString());
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        String userid = request.getParameter("userid");
        String password = request.getParameter("password");

        HttpSession session = request.getSession(true);
        if (isAccountLocked(userid)) {
            session.setAttribute("authNMsg", "msg.account.locked");
            response.sendRedirect("/login");
        } else if (authUser(userid, password)) {
            /* Reset account lock */
            User admin = userLoginHistory.get(userid);
            if (admin == null) {
                User newAdmin = new User();
                newAdmin.setUserId(userid);
                admin = userLoginHistory.putIfAbsent(userid, newAdmin);
                if (admin == null) {
                    admin = newAdmin;
                }
            }
            admin.setLoginFailedCount(0);
            admin.setLastLoginFailedTime(null);

            session.setAttribute("authNMsg", "authenticated");
            session.setAttribute("userid", userid);
            
            String target = (String) session.getAttribute("target");
            if (target == null) {
                response.sendRedirect("/admins/main");
            } else {
                session.removeAttribute("target");
                response.sendRedirect(target);
            }
        } else {
            /* account lock count +1 */
            User admin = userLoginHistory.get(userid);
            if (admin == null) {
                User newAdmin = new User();
                newAdmin.setUserId(userid);
                admin = userLoginHistory.putIfAbsent(userid, newAdmin);
                if (admin == null) {
                    admin = newAdmin;
                }
            }
            admin.setLoginFailedCount(admin.getLoginFailedCount() + 1);
            admin.setLastLoginFailedTime(new Date());
            
            session.setAttribute("authNMsg", "msg.authentication.fail");
            doGet(request, response) ;
        }
    }

    protected boolean isAccountLocked(String userid) {
        User admin = userLoginHistory.get(userid);
        if (admin != null
                && admin.getLoginFailedCount() == ApplicationUtils.getAccountLockCount()
                && (new Date().getTime() - admin.getLastLoginFailedTime().getTime() < ApplicationUtils
                        .getAccountLockTime())) {
            return true;
        }
        return false;
    }

    protected boolean authUser(String username, String password) {
        
        ExprNode filter = null;
        EntryFilteringCursor cursor = null;
        try {
            filter = FilterParser.parse("(&(uid=" + ESAPI.encoder().encodeForLDAP(username.trim())
                    + ")(userPassword=" + ESAPI.encoder().encodeForLDAP(password.trim()) + "))");
            cursor = EmbeddedADS.getAdminSession().search(new LdapDN("ou=people,dc=t246osslab,dc=org"),
                    SearchScope.SUBTREE, filter, AliasDerefMode.NEVER_DEREF_ALIASES, null);
            if (cursor.available()) {
                return true;
            }
        } catch (Exception e) {
            log.error("Exception occurs: ", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    log.error("Exception occurs: ", e);
                }
            }
        }
        return false;
    }
}
