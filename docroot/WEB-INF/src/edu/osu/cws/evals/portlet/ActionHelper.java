package edu.osu.cws.evals.portlet;

import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.ParamUtil;
import edu.osu.cws.evals.hibernate.*;
import edu.osu.cws.evals.models.*;
import edu.osu.cws.evals.util.EvalsPDF;
import edu.osu.cws.evals.util.HibernateUtil;
import edu.osu.cws.evals.util.Mailer;
import org.apache.commons.configuration.CompositeConfiguration;
import org.hibernate.Session;

import javax.portlet.*;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Pattern;

/**
 * ActionHelper class used to map user form actions to respective class methods.
 */
public class ActionHelper {
    public static final String ROLE_ADMINISTRATOR = "administrator";
    public static final String ROLE_REVIEWER = "reviewer";
    public static final String ROLE_SUPERVISOR = "supervisor";
    public static final String ROLE_SELF = "self";
    public static final String ALL_MY_ACTIVE_APPRAISALS = "allMyActiveAppraisals";
    public static final String MY_TEAMS_ACTIVE_APPRAISALS = "myTeamsActiveAppraisals";
    private static final String REVIEW_LIST = "reviewList";
    private static final String REVIEW_LIST_MAX_RESULTS = "reviewListMaxResults";
    public static final String APPRAISAL_NOT_FOUND = "We couldn't find your appraisal. If you believe this is an " +
            "error, please contact your supervisor.";

    private EmployeeMgr employeeMgr = new EmployeeMgr();

    private JobMgr jobMgr = new JobMgr();

    private AdminMgr adminMgr = new AdminMgr();

    private ReviewerMgr reviewerMgr = new ReviewerMgr();

    private ConfigurationMgr configurationMgr = new ConfigurationMgr();

    private PortletContext portletContext;

    public static final String ACCESS_DENIED = "You do not have access to perform this action.";

    private HashMap<String, Object> requestMap = new HashMap<String, Object>();

    /**
     * Specifies whether or not the request is an AJAX request by checking whether or not
     * request and response are instances of ResourceRequest and ResourceResponse.
     *
     * @param request
     * @param response
     * @return
     */
    public boolean isAJAX(PortletRequest request, PortletResponse response) {
        return request instanceof ResourceRequest && response instanceof ResourceResponse;
    }

    /**
     * Places in the request object the active appraisals of the user. This is used by the notification
     * piece.
     *
     * @param request
     * @param employeeId    Id/Pidm of the currently logged in user
     * @throws Exception
     */
    public void setupMyActiveAppraisals(PortletRequest request, int employeeId)
            throws Exception {
        List<Appraisal> allMyActiveAppraisals = getMyActiveAppraisals(request, employeeId);
        requestMap.put("myActiveAppraisals", allMyActiveAppraisals);
    }

    /**
     * Tries to fetch the employee active appraisals from session and if they are null, it grabs them from
     * the db.
     *
     * @param request
     * @param employeeId
     * @return
     * @throws Exception
     */
    public List<Appraisal> getMyActiveAppraisals(PortletRequest request, int employeeId) throws Exception {
        PortletSession session = request.getPortletSession(true);
        List<Appraisal> allMyActiveAppraisals;

        allMyActiveAppraisals = (ArrayList<Appraisal>) session.getAttribute(ALL_MY_ACTIVE_APPRAISALS);
        if (allMyActiveAppraisals == null) {
            AppraisalMgr appraisalMgr = new AppraisalMgr();
            allMyActiveAppraisals = appraisalMgr.getAllMyActiveAppraisals(employeeId);
            session.setAttribute(ALL_MY_ACTIVE_APPRAISALS, allMyActiveAppraisals);
        }
        return allMyActiveAppraisals;
    }

    /**
     * Fetches the supervisor's team active appraisal and stores the list in session. Then it places the list
     * in the requestMap so that the view can access it.
     *
     * @param request
     * @param employeeId    Id/Pidm of the currently logged in user
     * @throws Exception
     */
    public void setupMyTeamActiveAppraisals(PortletRequest request, int employeeId) throws Exception {
        if (isLoggedInUserSupervisor(request)) {
            ArrayList<Appraisal> myTeamAppraisals = getMyTeamActiveAppraisals(request, employeeId);
            requestMap.put(MY_TEAMS_ACTIVE_APPRAISALS, myTeamAppraisals);
        }
    }

    /**
     * Tries to fetch the my teams active appraisals from session. If they list is null, it fetches them
     * from the db.
     *
     * @param request       PortletRequest
     * @param employeeId    Id of the logged in user
     * @return              ArrayList<Appraisal>
     * @throws Exception
     */
    public ArrayList<Appraisal> getMyTeamActiveAppraisals(PortletRequest request, int employeeId) throws Exception {
        PortletSession session = request.getPortletSession(true);

        ArrayList<Appraisal> myTeamAppraisals;
        List<Appraisal> dbTeamAppraisals;
        myTeamAppraisals = (ArrayList<Appraisal>) session.getAttribute(MY_TEAMS_ACTIVE_APPRAISALS);
        if (myTeamAppraisals == null) {
            AppraisalMgr appraisalMgr = new AppraisalMgr();
            dbTeamAppraisals = appraisalMgr.getMyTeamsAppraisals(employeeId, true);
            myTeamAppraisals = new ArrayList<Appraisal>();

            if (dbTeamAppraisals != null) {
                for (Appraisal appraisal : dbTeamAppraisals) {
                    appraisal.setRole("supervisor");
                    myTeamAppraisals.add(appraisal);
                }
            }
            session.setAttribute(MY_TEAMS_ACTIVE_APPRAISALS, myTeamAppraisals);
        }
        return myTeamAppraisals;
    }

    /**
     * Checks the user permission level and sets up some flags in the session object to store those
     * permissions.
     *
     * @param request
     * @param refresh   Update the user permissions, even if they have already been set
     * @throws Exception
     */
    public void setUpUserPermissionInSession(PortletRequest request, boolean refresh) throws Exception {
        PortletSession session = request.getPortletSession(true);
        Employee employee = getLoggedOnUser(request);
        int employeeId = employee.getId();

        Boolean isSupervisor = (Boolean) session.getAttribute("isSupervisor");
        if (refresh || isSupervisor == null) {
            isSupervisor = jobMgr.isSupervisor(employeeId);
            session.setAttribute("isSupervisor", isSupervisor);
        }
        requestMap.put("isSupervisor", isSupervisor);

        Boolean isReviewer = (Boolean) session.getAttribute("isReviewer");
        if (refresh || isReviewer == null) {
            isReviewer = getReviewer(employeeId) != null;
            session.setAttribute("isReviewer", isReviewer);
        }
        requestMap.put("isReviewer", isReviewer);

        Boolean isMasterAdmin = (Boolean) session.getAttribute("isSuperAdmin");
        if (refresh || isMasterAdmin == null) {
            if (getAdmin(employeeId) != null && getAdmin(employeeId).getIsMaster()) {
                isMasterAdmin = true;
            } else {
                isMasterAdmin = false;
            }
            session.setAttribute("isMasterAdmin", isMasterAdmin);
        }
        requestMap.put("isMasterAdmin", isMasterAdmin);

        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        if (refresh || isAdmin == null) {
            isAdmin = getAdmin(employeeId) != null;
            session.setAttribute("isAdmin", isAdmin);
        }
        requestMap.put("isAdmin", isAdmin);

        requestMap.put("employee", getLoggedOnUser(request));
    }

    /**
     * Updates the admins List in the portletContext. This method is called by
     * EvalsPortlet.portletSetup and by AdminsAction.add methods.
     *
     * @param updateContextTimestamp    Whether or not to update the context timestamp in config_times
     * @throws Exception
     */
    public void setEvalsAdmins(boolean updateContextTimestamp) throws Exception {
        portletContext.setAttribute("admins", adminMgr.mapByEmployeeId());
        List<Admin> admins = adminMgr.list();
        // Call getName on the admins object to initialize the employee name
        for (Admin admin : admins) {
            if (admin.getEmployee() != null) {
                admin.getEmployee().getName();
            }
        }
        portletContext.setAttribute("adminsList", admins);
        if (updateContextTimestamp) {
            updateContextTimestamp();
        }
    }

    /**
     * Updates the reviewers List in the portletContext. This method is called by
     * EvalsPortlet.portletSetup and by ReviewersAction.add methods.
     *
     * @param updateContextTimestamp    Whether or not to update the context timestamp in config_times
     * @throws Exception
     */
    public void setEvalsReviewers(boolean updateContextTimestamp) throws Exception {
        portletContext.setAttribute("reviewers", reviewerMgr.mapByEmployeeId());
        List<Reviewer> reviewers = reviewerMgr.list();
        // Call getName on the reviewers object to initialize the employee name
        for (Reviewer reviewer : reviewers) {
            if (reviewer.getEmployee() != null) {
                reviewer.getEmployee().getName();
            }
        }
        portletContext.setAttribute("reviewersList", reviewers);
        if (updateContextTimestamp) {
            updateContextTimestamp();
        }
    }

    /**
     * Updates the configuration List in the portletContext. This method is called by
     * EvalsPortlet.portletSetup and by ConfigurationsAction.edit methods.
     *
     * @param updateContextTimestamp    Whether or not to update the context timestamp in config_times
     * @throws Exception
     */
    public void setEvalsConfiguration(boolean updateContextTimestamp) throws Exception {
        portletContext.setAttribute("configurations", configurationMgr.mapByName());
        portletContext.setAttribute("configurationsList", configurationMgr.list());
        if (updateContextTimestamp) {
            updateContextTimestamp();
        }
    }

    /**
     * Sets a request parameter to tell the jsp to use the normal top menu.
     *
     * @param request
     */
    public void useNormalMenu(PortletRequest request) {
        requestMap.put("menuHome", true);
    }

    /**
     * Sets a request parameter to tell the jsp to use the maximized top menu.
     *
     * @param request
     */
    public void useMaximizedMenu(PortletRequest request) {
        addToRequestMap("menuMax", true);
    }

    /**
     * Returns the reviews for the logged on User. It is a wrapper for
     * getReviewsForLoggedInUser(request, maxResults). It basically looks up in session what is
     * the number of maxResults and calls the getReviewsForLoggedInUser method.
     *
     * @param request       PortletRequest object
     * @return              ArrayList<Appraisal>
     * @throws Exception
     */
    private ArrayList<Appraisal> getReviewsForLoggedInUser(PortletRequest request) throws Exception {
        int defaultMaxResults = -1;
        PortletSession session = request.getPortletSession(true);
        Integer maxResults = (Integer) session.getAttribute(REVIEW_LIST_MAX_RESULTS);
        if (maxResults == null) {
            maxResults = defaultMaxResults;
        }

        return getReviewsForLoggedInUser(request, maxResults);
    }

    /**
     * Retrieves the pending reviews for the logged in user.
     *
     * @param request
     * @param maxResults
     * @return
     * @throws Exception
     */
    public ArrayList<Appraisal> getReviewsForLoggedInUser(PortletRequest request, int maxResults) throws Exception {
        ArrayList<Appraisal> reviewList;
        int toIndex;
        ArrayList<Appraisal> outList = new ArrayList<Appraisal>();

        PortletSession session = request.getPortletSession(true);
        reviewList = (ArrayList<Appraisal>) session.getAttribute(REVIEW_LIST);
        session.setAttribute(REVIEW_LIST_MAX_RESULTS, maxResults);

        if (reviewList == null) { //No data yet, need to get it from the database.
            String businessCenterName = ParamUtil.getString(request, "businessCenterName");

            if (businessCenterName.equals("")) {
                int employeeID = getLoggedOnUser(request).getId();
                businessCenterName = getReviewer(employeeID).getBusinessCenterName();
            }
            AppraisalMgr appraisalMgr = new AppraisalMgr();
            reviewList = appraisalMgr.getReviews(businessCenterName, -1);
            session.setAttribute(REVIEW_LIST, reviewList);
        }

        if (maxResults == -1 || reviewList.size() < maxResults) {
            toIndex = reviewList.size();
        } else {
            toIndex = maxResults;
        }

        for (int i = 0; i < toIndex; i++) {
            outList.add(reviewList.get(i));
        }

        return outList;
    }

    /**
     * Setups up parameters from portletContext needed by AppraisalMgr class.
     *
     * @param currentlyLoggedOnUser
     * @param appraisalMgr
     */
    public void setAppraisalMgrParameters(Employee currentlyLoggedOnUser, AppraisalMgr appraisalMgr) {
        HashMap permissionRules = (HashMap) portletContext.getAttribute("permissionRules");
        HashMap<Integer, Admin> admins = (HashMap<Integer, Admin>) portletContext.getAttribute("admins");
        HashMap<Integer, Reviewer> reviewers = (HashMap<Integer, Reviewer>) portletContext.getAttribute("reviewers");
        HashMap appraisalSteps = (HashMap) portletContext.getAttribute("appraisalSteps");
        Mailer mailer = (Mailer) portletContext.getAttribute("mailer");
        Map<String, Configuration> configurationMap =
                (Map<String, Configuration>) portletContext.getAttribute("configurations");

        appraisalMgr.setPermissionRules(permissionRules);
        appraisalMgr.setLoggedInUser(currentlyLoggedOnUser);
        appraisalMgr.setAdmins(admins);
        appraisalMgr.setReviewers(reviewers);
        appraisalMgr.setAppraisalSteps(appraisalSteps);
        appraisalMgr.setMailer(mailer);
        appraisalMgr.setConfigurationMap(configurationMap);

    }

    /**
     * Handles removing an appraisal from the reviewList stored in session. This method is called
     * by the AppraisalsAction.update method after a reviewer submits a review.
     *
     * @param request
     * @param appraisal
     * @throws Exception
     */
    public void removeReviewAppraisalInSession(PortletRequest request, Appraisal appraisal) throws Exception {
        List<Appraisal> reviewList = getReviewsForLoggedInUser(request);
        List<Appraisal> tempList = new ArrayList<Appraisal>();
        tempList.addAll(reviewList);
        for (Appraisal appraisalInSession: tempList) {
            if (appraisalInSession.getId() == appraisal.getId()) {
                reviewList.remove(appraisalInSession);
                break;
            }
        }

        PortletSession session = request.getPortletSession(true);
        session.setAttribute(REVIEW_LIST, reviewList);
    }

    /**
     * Checks if the context cache is outdated and refreshes the context cache:
     * admins, reviewers and configuration lists and maps. If the context cache is refreshed, it
     * updates the context cache timestamp in the portlet context.
     *
     * @throws Exception
     */
    public void refreshContextCache() throws Exception {
        Date contextCacheTimestamp = (Date) portletContext.getAttribute(EvalsPortlet.CONTEXT_CACHE_TIMESTAMP);
        Timestamp contextLastUpdate = ConfigurationMgr.getContextLastUpdate();
        if (contextLastUpdate.after(contextCacheTimestamp)) {
            setEvalsAdmins(false);
            setEvalsReviewers(false);
            setEvalsConfiguration(false);
            portletContext.setAttribute(EvalsPortlet.CONTEXT_CACHE_TIMESTAMP, new Date());
        }
    }

    /**
     * Updates the context timestamp in the db and also in the portletContext.
     * @throws Exception
     */
    private void updateContextTimestamp() throws Exception {
        Date currentTimestamp = ConfigurationMgr.updateContextTimestamp();
        portletContext.setAttribute(EvalsPortlet.CONTEXT_CACHE_TIMESTAMP, currentTimestamp);
    }

    /**
     * Takes an string error message and sets in the session.
     *
     * @param request
     * @param errorMsg
     */
    public void addErrorsToRequest(PortletRequest request, String errorMsg) {
        addToRequestMap("errorMsg", errorMsg);
    }

    /**
     * Returns an Employee object of the currently logged on user. First it looks in
     * the PortletSession if it's not there it fetches the Employee object and stores
     * it there.
     *
     * @param request   PortletRequest
     * @return
     * @throws Exception
     */
    public Employee getLoggedOnUser(PortletRequest request) throws Exception {
        PortletSession session = request.getPortletSession(true);
        Employee loggedOnUser = (Employee) session.getAttribute("loggedOnUser");
        if (loggedOnUser == null) {
            String loggedOnUsername = getLoggedOnUsername(request);
            loggedOnUser = employeeMgr.findByOnid(loggedOnUsername, "employee-with-jobs");

            // Initialize the jobs and supervisor of the jobs so that display employment
            // information has the data it needs.
            Set<Job> jobs = loggedOnUser.getNonTerminatedJobs();
            if (jobs != null && !jobs.isEmpty()) {
                for (Job job : jobs) {
                    if (job.getSupervisor() != null && job.getSupervisor().getEmployee() != null) {
                        job.getSupervisor();
                        job.getSupervisor().getEmployee();
                        job.getSupervisor().getEmployee().getName();
                    }
                }
            }
            session.setAttribute("loggedOnUser", loggedOnUser);
            refreshContextCache();
        }

        return loggedOnUser;
    }

    /**
     * Returns a map with information on the currently logged on user.
     *
     * @param request
     * @return
     */
    private Map getLoggedOnUserMap(PortletRequest request) {
        return (Map)request.getAttribute(PortletRequest.USER_INFO);
    }

    /**
     * Returns the username of the currently logged on user. If there is no valid username, it
     * returns an empty string.
     *
     * @param request
     * @return username
     */
    public String getLoggedOnUsername(PortletRequest request) {
        PortletSession session = request.getPortletSession(true);
        String usernameSessionKey = "onidUsername";
        String onidUsername = (String) session.getAttribute(usernameSessionKey);
        if (onidUsername == null || onidUsername.equals("")) {
            Map userInfo = getLoggedOnUserMap(request);

            String screenName = "";
            if (userInfo != null) {
                screenName = (String) userInfo.get("user.name.nickName");
            }
            // If the screenName is numeric it means that we are using the Oracle db and
            // we need to query banner to fetch the onid username
            if (Pattern.matches("[0-9]+", screenName)) {
                CompositeConfiguration config = (CompositeConfiguration) portletContext.getAttribute("environmentProp");
                String bannerHostname = config.getString("banner.hostname");
                onidUsername = EmployeeMgr.getOnidUsername(screenName, bannerHostname);
            } else {
                onidUsername = screenName;
            }
            session.setAttribute(usernameSessionKey, onidUsername);
        }
        return onidUsername;
    }

    /**
     * Sets the porletContext field. This method is called by the delegate and portletSetup
     * methods in EvalsPortlet.
     *
     * @param portletContext
     */
    public void setPortletContext(PortletContext portletContext) {
        this.portletContext = portletContext;
    }

    public PortletContext getPortletContext() {
        return portletContext;
    }

    /**
     * Returns an attribute from the portletContext
     *
     * @param key
     * @return
     */
    public Object getPortletContextAttribute(String key) {
        return portletContext.getAttribute(key);
    }

    /**
     * Takes in a pidm, and looks up in the reviewers HashMap stored in the portlet context
     * to figure out if the current logged in user is a reviewer. If yes, then we return the
     * Reviewer object if not, it returns null.
     *
     * @param pidm  Pidm of currently logged in user
     * @return Reviewer
     */
    public Reviewer getReviewer(int pidm) {
        HashMap<Integer, Reviewer> reviewerMap =
                (HashMap<Integer, Reviewer>) portletContext.getAttribute("reviewers");

        return reviewerMap.get(pidm);
    }

    /**
     * Takes in a pidm, and looks up in the admins HashMap stored in the portlet context
     * to figure out if the current logged in user is a reviewer. If yes, then we return the
     * Admin object if not, it returns false.
     *
     * @param pidm
     * @return Admin
     */
    private Admin getAdmin(int pidm) {
        HashMap<Integer, Admin> adminMap =
                (HashMap<Integer, Admin>) portletContext.getAttribute("admins");

        return adminMap.get(pidm);
    }

    /**
     * Returns true if the logged in user is admin, false otherwise.
     *
     * @param request
     * @return boolean
     * @throws Exception
     */
    public boolean isLoggedInUserAdmin(PortletRequest request) throws Exception {
        PortletSession session = request.getPortletSession(true);
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        if (isAdmin == null) {
            setUpUserPermissionInSession(request, false);
            isAdmin = (Boolean) session.getAttribute("isAdmin");
        }
        return isAdmin;
    }

    /**
     * Returns true if the logged in user is a master admin, false otherwise.
     *
     * @param request
     * @return boolean
     * @throws Exception
     */
    public boolean isLoggedInUserMasterAdmin(PortletRequest request) throws Exception {
        PortletSession session = request.getPortletSession(true);
        Boolean isMasterAdmin = (Boolean) session.getAttribute("isMasterAdmin");
        if (isMasterAdmin == null) {
            setUpUserPermissionInSession(request, false);
            isMasterAdmin = (Boolean) session.getAttribute("isMasterAdmin");
        }
        return isMasterAdmin;
    }

    /**
     * Returns true if the logged in user is reviewer, false otherwise.
     *
     * @param request
     * @return boolean
     * @throws Exception
     */
    public boolean isLoggedInUserReviewer(PortletRequest request) throws Exception {
        PortletSession session = request.getPortletSession(true);
        Boolean isReviewer = (Boolean) session.getAttribute("isReviewer");
        if (isReviewer == null) {
            setUpUserPermissionInSession(request, false);
            isReviewer = (Boolean) session.getAttribute("isReviewer");
        }
        return isReviewer;
    }

    /**
     * Returns true if the logged in user is a supervisor, false otherwise.
     *
     * @param request
     * @return boolean
     * @throws Exception
     */
    public boolean isLoggedInUserSupervisor(PortletRequest request) throws Exception {
        PortletSession session = request.getPortletSession(true);
        Boolean isSupervisor = (Boolean) session.getAttribute("isSupervisor");
        if (isSupervisor == null) {
            setUpUserPermissionInSession(request, false);
            isSupervisor = (Boolean) session.getAttribute("isSupervisor");
        }
        return isSupervisor;
    }

    /**
     * Using the request object, it fetches the list of employee appraisals and supervisor
     * appraisals and finds out if there are any actions required for them. It also checks
     * to see if the user is a reviewer and it gets the action required for the reviewer.
     * It sets two attributes in the request object: employeeActions and administrativeActions.
     *
     * @param request
     * @return ArrayList<RequiredAction>
     * @throws Exception
     */
    public void setRequiredActions(PortletRequest request) throws Exception {
        ArrayList<RequiredAction> employeeRequiredActions;
        ArrayList<RequiredAction> administrativeActions = new ArrayList<RequiredAction>();
        ArrayList<Appraisal> myActiveAppraisals;
        ArrayList<Appraisal> supervisorActions;
        RequiredAction reviewerAction;
        Reviewer reviewer;
        Employee loggedInEmployee = getLoggedOnUser(request);
        int employeeID = loggedInEmployee.getId();
        ResourceBundle resource = (ResourceBundle) portletContext.getAttribute("resourceBundle");


        myActiveAppraisals = (ArrayList<Appraisal>) requestMap.get("myActiveAppraisals");
        employeeRequiredActions = getAppraisalActions(myActiveAppraisals, "employee", resource);
        requestMap.put("employeeActions", employeeRequiredActions);

        // add supervisor required actions, if user has team's active appraisals
        if (requestMap.get("myTeamsActiveAppraisals") != null) {
            supervisorActions = (ArrayList<Appraisal>) requestMap.get("myTeamsActiveAppraisals");
            administrativeActions = getAppraisalActions(supervisorActions, "supervisor", resource);
        }

        reviewer = getReviewer(employeeID);
        if (reviewer != null) {
            String businessCenterName = reviewer.getBusinessCenterName();
            reviewerAction = getReviewerAction(businessCenterName, resource, request);
            if (reviewerAction != null) {
                administrativeActions.add(reviewerAction);
            }
        }
        requestMap.put("administrativeActions", administrativeActions);
    }

    /**
     * Returns a list of actions required for the given user and role, based on the
     * list of appraisals passed in. If the user and role have no appraisal actions,
     * it returns an empty ArrayList.
     *
     * @param appraisalList     List of appraisals to check for actions required
     * @param role              Role of the currently logged in user
     * @param resource          Resource bundle to pass in to RequiredAction bean
     * @return  outList
     * @throws edu.osu.cws.evals.models.ModelException
     */
    public ArrayList<RequiredAction> getAppraisalActions(List<Appraisal> appraisalList,
                                                         String role, ResourceBundle resource) throws Exception {
        Configuration configuration;
        HashMap permissionRuleMap = (HashMap) portletContext.getAttribute("permissionRules");
        Map<String, Configuration> configurationMap =
                (Map<String, Configuration>) portletContext.getAttribute("configurations");

        ArrayList<RequiredAction> outList = new ArrayList<RequiredAction>();
        String actionKey = "";
        RequiredAction actionReq;
        HashMap<String, String> anchorParams;

        for (Appraisal appraisal : appraisalList) {
            //get the status, compose the key "status"-"role"
            String appraisalStatus = appraisal.getStatus();
            actionKey = appraisalStatus +"-"+role;

            // Get the appropriate permissionrule object from the permissionRuleMap
            PermissionRule rule = (PermissionRule) permissionRuleMap.get(actionKey);
            String actionRequired = "";
            if (rule != null) {
                actionRequired = rule.getActionRequired();
            }
            if (actionRequired != null && !actionRequired.equals("")) {
                // compose a requiredAction object and add it to the outList.
                anchorParams = new HashMap<String, String>();
                anchorParams.put("action", "display");
                anchorParams.put("controller", "AppraisalsAction");
                String appraisalID = Integer.toString(appraisal.getId());
                anchorParams.put("id", appraisalID);
                if (appraisalStatus.equals(Appraisal.STATUS_GOALS_REQUIRED_MODIFICATION) ||
                        appraisalStatus.equals(Appraisal.STATUS_GOALS_REACTIVATED)) {
                    configuration = configurationMap.get(Appraisal.STATUS_GOALS_DUE);
                } else {
                    if (appraisalStatus.contains("Overdue")) {
                        appraisalStatus = appraisalStatus.replace("Overdue", "Due");
                    }
                    configuration = configurationMap.get(appraisalStatus);
                }
                if (configuration == null) {
                    throw new ModelException("Could not find configuration object for status - " + appraisalStatus);
                }

                actionReq = new RequiredAction();
                actionReq.setParameters(anchorParams);
                actionReq.setAnchorText(actionRequired, appraisal, resource, configuration);
                outList.add(actionReq);
            }
        }
        return outList;
    }

    /**
     * Returns the required action for the business center reviewer.
     *
     * @param businessCenterName
     * @param resource
     * @return
     * @throws Exception
     */
    private RequiredAction getReviewerAction(String businessCenterName, ResourceBundle resource,
                                             PortletRequest request) throws Exception {
        int reviewCount;
        List<Appraisal> reviewList = getReviewsForLoggedInUser(request);
        if (reviewList != null) {
            reviewCount = reviewList.size();
        } else {
            AppraisalMgr appraisalMgr = new AppraisalMgr();
            reviewCount = appraisalMgr.getReviewCount(businessCenterName);
        }

        RequiredAction requiredAction = new RequiredAction();
        if (reviewCount == 0) {
            return null;
        }

        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("action", "reviewList");
        parameters.put("controller", "AppraisalsAction");
        requiredAction.setAnchorText("action-required-review", reviewCount, resource);
        requiredAction.setParameters(parameters);

        return requiredAction;
    }

    /**
     * Sets the entries in requestMap in the RenderRequest object and clears the requestMap
     * afterwards.
     *
     * @param request
     */
    public void setRequestAttributes(RenderRequest request) {
        String currentRole = getCurrentRole(request);
        requestMap.put("currentRole", currentRole);

        for (Map.Entry<String, Object> entry : requestMap.entrySet()) {
            request.setAttribute(entry.getKey(), entry.getValue());
        }
        requestMap.clear();

    }

    public void addToRequestMap(String key, Object object) {
        requestMap.put(key, object);
    }

    public Object getFromRequestMap(String key) {
        return requestMap.get(key);
    }

    /**
     * Returns the currentRole that the logged in user last selected. It
     * tries to grab it from the request and it stores it in session.
     *
     * @param request
     * @return
     */
    public String getCurrentRole(PortletRequest request) {
        PortletSession session = request.getPortletSession(true);
        String currentRole = (String) session.getAttribute("currentRole");

        String roleFromRequest = ParamUtil.getString(request, "currentRole");
        if (!roleFromRequest.equals("")) {
            currentRole = roleFromRequest;
            session.setAttribute("currentRole", currentRole);
        }

        if (currentRole == null || currentRole.equals("")) {
            currentRole = ROLE_SELF;
            session.setAttribute("currentRole", currentRole);
        }

        return currentRole;
    }

}