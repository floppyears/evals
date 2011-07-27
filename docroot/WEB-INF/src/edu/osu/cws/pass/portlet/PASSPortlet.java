/**
 * Portlet class for PASS project, used to handle the processAction and doView
 * requests. It relies heavily on the Actions class to figure out which classes
 * to delegate the work to.
 */

package edu.osu.cws.pass.portlet;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import edu.osu.cws.pass.hibernate.AppraisalStepMgr;
import edu.osu.cws.pass.hibernate.PermissionRuleMgr;
import edu.osu.cws.pass.util.*;
import edu.osu.cws.util.CWSUtil;
import edu.osu.cws.util.Logger;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.portlet.*;

/**
 * <a href="PASSPortlet.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 *
 */
public class PASSPortlet extends GenericPortlet {

    /**
     * String used to store the view jsp used by the
     * doView method.
     */
	protected String viewJSP;

    /**
     * Name of default properties file that is loaded the first time the
     * portlet is loaded.
     */
    private static final String defaultProperties = "default.properties";

    private PermissionRuleMgr permissionRuleMgr = new PermissionRuleMgr();
    private AppraisalStepMgr appraisalStepMgr = new AppraisalStepMgr();

    /**
     * Helper Liferay object to store error messages into the server's log file
     */
	private static Log _log = LogFactoryUtil.getLog(PASSPortlet.class);

    /**
     * The actions class
     */
    private Actions actionClass = new Actions();

    public void doDispatch(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		String jspPage = renderRequest.getParameter("jspPage");

		if (jspPage != null) {
			include(jspPage, renderRequest, renderResponse);
		}
		else {
			super.doDispatch(renderRequest, renderResponse);
		}
	}

    /**
     * This method expects an "action" as a renderRequest parameter. If there is one, it tries
     * to call Actions.action method. If that method does not exist it calls the default home
     * action.
     *
     * @param renderRequest     Portlet RenderRequest object
     * @param renderResponse    Portlet RenderResponse object
     * @throws IOException
     * @throws PortletException
     */
	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

        try {
            portletSetup(renderRequest);
            actionClass.setUpUserPermissionInSession(renderRequest, false);
        } catch (Exception e) {
            handlePASSException(e, "Error in doView", Logger.CRITICAL, true);
        }

        // If processAction's delegate method was called, it set the viewJSP property to some
        // jsp value, if viewJSP is null, it means processAction was not called and we need to
        // call delegate
        if (viewJSP == null) {
            delegate(renderRequest, renderResponse);
        }

        include(viewJSP, renderRequest, renderResponse);
        viewJSP = null;
	}

    public void processAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws IOException, PortletException {

        try {
            portletSetup(actionRequest);
            actionClass.setUpUserPermissionInSession(actionRequest, false);
            delegate(actionRequest, actionResponse);
        } catch (Exception e) {
            handlePASSException(e, "Error in processAction", Logger.CRITICAL, true);
        }
	}

    public void serveResource(ResourceRequest request, ResourceResponse response)
            throws PortletException, IOException {
        String result = "";
        Method actionMethod;
        String resourceID;
        actionClass.setPortletContext(getPortletContext());

        response.setContentType("text/html");
        PrintWriter writer = response.getWriter();

        // The logic below is similar to delegate method, but instead we
        // need to return the value we get from Actions method instead of
        // assign it to viewJSP
        resourceID = request.getResourceID();
        if (resourceID != null) {
            try {
                actionMethod = Actions.class.getDeclaredMethod(resourceID, PortletRequest.class,
                        PortletResponse.class);

                // The resourceID methods return the init-param of the path
                result = (String) actionMethod.invoke(actionClass, request, response);
            } catch (Exception e) {
                handlePASSException(e, "Error in serveResource", Logger.ERROR, false);
                result="There was an error performing your request";
            }
        } else {
            result="There was an error performing your request";
        }
        writer.print(result);
    }

    /**
     * Delegate method will call the respective method in the Actions class and pass the request
     * and response objects. The method called in Actions is based on the "action" parameter value.
     * The delegated methods in Actions class return the path to the jsp file that should be loaded.
     * Those methods also set any parameters needed by the jsp files.
     *
     * @param request   PortletRequest
     * @param response  PortletResponse
     */
    public void delegate(PortletRequest request, PortletResponse response) {
        Method actionMethod;
        String action;
        actionClass.setPortletContext(getPortletContext());
        viewJSP = "home-jsp";

        // The portlet action can be set by the action/renderURLs using "action" as the parameter
        // name
        action =  ParamUtil.getString(request, "action", "displayHomeView");

        if (!action.equals("")) {
            try {
                actionMethod = Actions.class.getDeclaredMethod(action, PortletRequest.class,
                        PortletResponse.class);

                // The action methods return the init-param of the path
                viewJSP = (String) actionMethod.invoke(actionClass, request, response);
            } catch (Exception e) {
                handlePASSException(e, action, Logger.ERROR, false);
            }
        }

        // viewJSP holds an init parameter that maps to a jsp file
        viewJSP = getInitParameter(viewJSP);
    }

    /**
     * Takes care of loading the resource bundle Language.properties into the
     * portletContext.
     * @throws java.util.MissingResourceException
     */
    private void loadResourceBundle() throws MissingResourceException{
        ResourceBundle resources = ResourceBundle.getBundle("edu.osu.cws.pass.portlet.Language");
        getPortletContext().setAttribute("resourceBundle", resources);
    }

    /**
     * Takes care of initializing portlet variables and storing them in the portletContext.
     * Some of these variables are: permissionRuleMgr, appraisalStepMgr, reviewers, admins and
     * environment properties. This method is called everytime a doView, processAction or
     * serveResource are called, but the code inside only executes the first time.
     *
     * @param request
     * @throws Exception
     */
    private void portletSetup(PortletRequest request) throws Exception {
        if (getPortletContext().getAttribute("environmentProp") == null) {
            loadEnvironmentProperties(request);
            createLogger();
            getPortletContext().setAttribute("permissionRules", permissionRuleMgr.list());
            getPortletContext().setAttribute("appraisalSteps", appraisalStepMgr.list());
            loadResourceBundle();

            actionClass.setPortletContext(getPortletContext());
            actionClass.setPassAdmins();
            actionClass.setPassReviewers();
            actionClass.setPassConfiguration();
        }
    }

    /**
     * Creates an instance of PassLogger, grabs the properties from the properties file and stores the
     * log instance in the portletContext.
     */
    private void createLogger() {
        CompositeConfiguration config = (CompositeConfiguration) getPortletContext().getAttribute("environmentProp");
        String serverName = config.getString("log.serverName");
        String clientHost = config.getString("log.clientHost");
        PassLogger passLogger = new PassLogger(serverName, clientHost);
        getPortletContext().setAttribute("log", passLogger);
    }

    /**
     * Returns a PassLogger instance stored in the portletContext.
     *
     * @return PassLogger
     * @throws Exception
     */
    private PassLogger getLog() throws Exception {
        PassLogger log = (PassLogger) getPortletContext().getAttribute("log");
        if (log == null) {
            throw new Exception("Could not get instance of PassLogger from portletContext");
        }
        return log;
    }

    /**
     * Loads default.properties and then overrides properties by trying to load
     * properties file: hostname.properties. The config object is then stored
     * in the portletContext.
     *
     * @param request
     * @throws Exception
     */
    private void loadEnvironmentProperties(PortletRequest request) throws Exception {
        String propertyFile = request.getServerName() +".properties";
        CompositeConfiguration config = new CompositeConfiguration();

        // First load hostname.properties. Then try to load default.properties
        PropertiesConfiguration propConfig =  new PropertiesConfiguration(propertyFile);
        if (propConfig.getFile().exists()) {
            config.addConfiguration(propConfig);
            _log.error(propertyFile + " - loaded");
        } else {
            _log.error(propertyFile + " - not found");
        }

        config.addConfiguration(new PropertiesConfiguration(defaultProperties));
        _log.error(defaultProperties + " - loaded");

        // Set the Hibernate config file and store properties in portletContext
        _log.error("using hibernate cfg file - "+config.getString("hibernate-cfg-file"));
        HibernateUtil.setConfig(config.getString("hibernate-cfg-file"));
        getPortletContext().setAttribute("environmentProp", config);
    }

    /**
     * Takes care of handling portletSetup errors. This method is called by doView,
     * processAction and delegate's catch block.
     *
     * @param e Exception
     * @param getInitParam  Whether or not to use getInitParameter when setting viewJSP
     */
    private void handlePASSException(Exception e, String shortMessage, String level, boolean getInitParam) {
        try {
            getLog().log(level, shortMessage, e);
        } catch (Exception exception) {
            _log.error(CWSUtil.stackTraceString(exception));
        }
        if (getInitParam) {
            viewJSP = getInitParameter("error-jsp");
        } else {
            viewJSP = "error-jsp";
        }
    }

    protected void include(
			String path, RenderRequest renderRequest,
			RenderResponse renderResponse)
		throws IOException, PortletException {

		PortletRequestDispatcher portletRequestDispatcher =
			getPortletContext().getRequestDispatcher(path);

		if (portletRequestDispatcher == null) {
            try {
                getLog().log(Logger.ERROR, path + " is not a valid include", "");
            } catch (Exception e) {
                _log.error(path + " is not a valid include");
                _log.error(CWSUtil.stackTraceString(e));
            }
		}
		else {
			portletRequestDispatcher.include(renderRequest, renderResponse);
		}
	}
}