package edu.osu.cws.evals.portlet;

import com.google.gson.Gson;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.ParamUtil;
import edu.osu.cws.evals.hibernate.*;
import edu.osu.cws.evals.models.*;
import edu.osu.cws.evals.util.*;
import edu.osu.cws.util.CWSUtil;
import edu.osu.cws.util.Logger;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.joda.time.DateTime;
import org.hibernate.Session;

import javax.portlet.*;
import java.io.File;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.*;

public class AppraisalsAction implements ActionInterface {
    private ActionHelper actionHelper;

    private HomeAction homeAction;

    private PortletRequest request;

    private Employee loggedInUser;

    private ResourceBundle resource;

    private ErrorHandler errorHandler;

    private Appraisal appraisal = null;

    private PermissionRule permRule = null;

    private String userRole;

    /**
     * Holds the structured json data that we get back from the evaluation form.
     */
    private AppraisalJSON jsonData = null;

    /**
     * Map that contains a map of all the assessments indexed by the assessment id. This makes
     * it easier to search for an assessment to update. Otherwise, we'd have to search for an
     * assessment within each goal version.
     */
    Map<String, Assessment> dbAssessmentsMap = null;

    /**
     * Handles displaying a list of pending reviews for a given business center.
     *
     * @param request   PortletRequest
     * @param response  PortletResponse
     * @return jsp      JSP file to display (defined in portlet.xml)
     * @throws Exception
     */
    public String reviewList(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);

        // Check that the logged in user is admin
        boolean isReviewer = actionHelper.getReviewer() != null;
        if (!isReviewer) {
            return errorHandler.handleAccessDenied(request, response);
        }

        ArrayList<Appraisal> appraisals = actionHelper.getReviewsForLoggedInUser(-1);
        actionHelper.addToRequestMap("appraisals", appraisals);
        actionHelper.addToRequestMap("pageTitle", "pending-reviews");
        actionHelper.useMaximizedMenu();

        return Constants.JSP_REVIEW_LIST;
    }

    /**
     * Initializes some private properties common to many methods.
     *
     * @param request
     * @throws Exception
     */
    public void initialize(PortletRequest request) throws Exception {
        this.request = request;
        this.resource = (ResourceBundle) actionHelper.getPortletContextAttribute("resourceBundle");
        this.loggedInUser = actionHelper.getLoggedOnUser();
        initializeAppraisal();
    }

    /**
     * Initializes an appraisal.
     *
     * @throws Exception
     */
    private void initializeAppraisal() throws Exception {
        int appraisalID = ParamUtil.getInteger(request, "id");
        if (appraisalID > 0) {
            appraisal = AppraisalMgr.getAppraisal(appraisalID);
            if(appraisal != null) {
                userRole = getRole();
                setPermRule();
                appraisal.setRole(userRole);
                appraisal.setPermissionRule(permRule);
            }
        }
    }

    /**
     * Figures out the current user role in the appraisal and returns the respective permission
     * rule for that user role and action in the appraisal. If the appraisal's status is like
     * "archived*" then it will remove the "archived" part of the status.
     *
     * @throws Exception
     */
    private void setPermRule() throws Exception {
        String status = appraisal.getStatus();
        if (status.contains("archived")) {
            status = status.replace("archived", "").toLowerCase();
        }

        HashMap permissionRules = (HashMap) actionHelper.getPortletContext().getAttribute("permissionRules");
        permRule = PermissionRuleMgr.getPermissionRule(permissionRules, appraisal, userRole);

        // Disable the employee/supervisor results if we are in the first round of goals (no approved goals yet)
        if (status.contains("goal") && appraisal.getApprovedGoalsVersions().isEmpty()) {
            permRule.setResults(null);
            permRule.setSupervisorResults(null);
        }
    }

    public PermissionRule getPermRule() {
        return permRule;
    }

    public Appraisal getAppraisal() {
        return appraisal;
    }

    /**
     * Returns the role (employee, supervisor, immediate supervisor or reviewer) of
     * the given appraisal.
     * Return empty string if the pidm does not have any role on the appraisal.
     *
     * @return role
     * @throws Exception
     */
    public String getRole() throws Exception {
        int pidm = loggedInUser.getId();

        if (pidm == appraisal.getJob().getEmployee().getId()) {
            return ActionHelper.ROLE_EMPLOYEE;
        }

        Job supervisor = appraisal.getJob().getSupervisor();
        if (supervisor != null && pidm == supervisor.getEmployee().getId()) {
            return ActionHelper.ROLE_SUPERVISOR;
        }

        Reviewer reviewer  = actionHelper.getReviewer();
        if (reviewer != null)
        {
            String bcName  = appraisal.getJob().getBusinessCenterName();
            if (bcName.equals(reviewer.getBusinessCenterName())) {
                return ActionHelper.ROLE_REVIEWER;
            }
        }

        // check admin role first because there are few admins and some jobs have a missing supervisor in the chain
        // which causes a NPE
        if (actionHelper.getAdmin() != null) {
            if (actionHelper.isLoggedInUserMasterAdmin()) {
                return ActionHelper.ROLE_MASTER_ADMIN;
            }
            return ActionHelper.ROLE_SUPER_ADMIN;
        }

        if (JobMgr.isUpperSupervisor(appraisal.getJob(), pidm)) {
            return ActionHelper.ROLE_UPPER_SUPERVISOR;
        }

        return "";
    }

    /**
     * Renders a list of appraisals based on the search criteria.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String search(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);
        List<Appraisal> appraisals = new ArrayList<Appraisal>();
        actionHelper.addToRequestMap("pageTitle", "search-results");
        boolean isAdmin = actionHelper.getAdmin() != null;
        boolean isReviewer = actionHelper.getReviewer() != null;

        // If a supervisor is also a reviewer, the people he/she supervises will be in the
        // business center he/she is a reviewer of. Because reviewer has broader permissions
        // than supervisor, we will use the reviewer's permission to do search.
        boolean isSupervisor = !isReviewer && actionHelper.isLoggedInUserSupervisor();

        if (!isAdmin && !isReviewer && !isSupervisor)  {
            return errorHandler.handleAccessDenied(request, response);
        }

        int pidm = loggedInUser.getId();
        String searchTerm = ParamUtil.getString(request, "searchTerm");
        if (StringUtils.isEmpty(searchTerm)) {
            actionHelper.addErrorsToRequest(resource.getString("appraisal-search-enter-id"));
        } else {
            String bcName = "";
            if (isReviewer) {
                bcName = actionHelper.getReviewer().getBusinessCenterName();
            }

            try {
                appraisals = AppraisalMgr.search(searchTerm, pidm, isSupervisor, bcName);

                if (appraisals.isEmpty()) {
                    if (isAdmin) {
                        actionHelper.addErrorsToRequest(resource.getString("appraisal-search-no-results-admin"));
                    } else if (isReviewer) {
                        actionHelper.addErrorsToRequest(resource.getString("appraisal-search-no-results-reviewer"));
                    } else {
                        actionHelper.addErrorsToRequest(resource.getString("appraisal-search-no-results-supervisor"));
                    }
                }
            } catch (ModelException e) {
                actionHelper.addErrorsToRequest(e.getMessage());
            }
        }

        actionHelper.addToRequestMap("appraisals", appraisals);
        actionHelper.useMaximizedMenu();

        return Constants.JSP_REVIEW_LIST;
    }

    /**
     * Handles displaying the appraisal when a user clicks on it. It loads the appraisal
     * object along with the respective permissionRule.
     *
     * @param request   PortletRequest
     * @param response  PortletResponse
     * @return jsp      JSP file to display (defined in portlet.xml)
     * @throws Exception
     */
    public String display(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);

        // Check to see if the logged in user has permission to access the appraisal
        if (permRule == null) {
            return errorHandler.handleAccessDenied(request, response);
        }

        actionHelper.setupMyTeamActiveAppraisals();

        // Check permission rules to decide which actions will be displayed
        if(!userRole.equals(ActionHelper.ROLE_EMPLOYEE)){

            if(permRule.getSendToNolij() != null){
                if(!isAdminRole()) {
                    ArrayList<Appraisal> reviews = actionHelper.getReviewsForLoggedInUser(-1);
                    actionHelper.addToRequestMap("pendingReviews", reviews);
                }
                if (appraisal.getEmployeeSignedDate() != null) {
                    actionHelper.addToRequestMap("displayResendNolij", true);
                }
            }

            if(permRule.getCloseOut() != null){
                actionHelper.addToRequestMap("displayCloseOutAppraisal", true);
            }

            if(permRule.getSetStatusToResultsDue() != null){
                actionHelper.addToRequestMap("displaySetAppraisalStatus", true);
            }
        }

        if(permRule.getReactivateGoals() != null){
            actionHelper.addToRequestMap("displayReactivateGoals", true);
        }

        if(permRule.getDownloadPDF() != null){
            actionHelper.addToRequestMap("displayDownloadPdf", true);
        }

        Map Notices = (Map)actionHelper.getPortletContextAttribute("Notices");
        actionHelper.addToRequestMap("appraisalNotice", Notices.get("Appraisal Notice"));
        appraisal.loadLazyAssociations();

        Map<String, List<Rating>> ratingsMap = (HashMap) actionHelper.getPortletContext().getAttribute("ratings");
        actionHelper.addToRequestMap("ratings", RatingMgr.getRatings(ratingsMap, appraisal.getAppointmentType()));

        actionHelper.addToRequestMap("appraisal", appraisal);
        actionHelper.addToRequestMap("permissionRule", permRule);
        Map<String, Configuration> configMap = (Map<String, Configuration>) actionHelper.getPortletContextAttribute("configurations");
        Configuration autoSaveFrequency = ConfigurationMgr.getConfiguration(configMap, "autoSaveFrequency",
                appraisal.getAppointmentType());
        actionHelper.addToRequestMap("autoSaveFrequency", autoSaveFrequency.getValue());
        actionHelper.useMaximizedMenu();

        if (appraisal.getIsSalaryUsed()) {
            setSalaryValues();
        }

        return Constants.JSP_APPRAISAL;
    }

    /**
     * Sets for the jsp the salary range values for Classified IT evaluations. The range and fixed
     * increase values depend on whether or not the current salary is above or below the control
     * point.
     */
    private void setSalaryValues() {
        Map<String, String> salaryValidationValues = getSalaryValidationValues();

        actionHelper.addToRequestMap("increaseRate2Value", salaryValidationValues.get("increaseRate2Value"));
        actionHelper.addToRequestMap("increaseRate1MinVal", salaryValidationValues.get("increaseRate1MinVal"));
        actionHelper.addToRequestMap("increaseRate1MaxVal", salaryValidationValues.get("increaseRate1MaxVal"));
    }

    /**
     * Returns a map with the correct salary increase validation values depending on whether or not
     * the current salary is above or below the midpoint.
     *
     * @return
     */
    private Map<String, String> getSalaryValidationValues() {
        Map<String, String> salaryValidationValues = new HashMap<String, String>();

        Salary salary = appraisal.getSalary();
        salaryValidationValues.put("increaseRate2Value", salary.getTwoIncrease().toString());
        salaryValidationValues.put("increaseRate1MinVal", salary.getOneMin().toString());
        salaryValidationValues.put("increaseRate1MaxVal", salary.getOneMax().toString());

        return salaryValidationValues;
    }

    /**
     * Handles updating the appraisal form.
     *
     * @param request   PortletRequest
     * @param response  PortletResponse
     * @return jsp      JSP file to display (defined in portlet.xml)
     * @throws Exception
     */
    public String update(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);
        boolean isReviewer = actionHelper.getReviewer() != null;

        // Check to see if the logged in user has permission to access the appraisal
        boolean isAjax = actionHelper.isAJAX();
        if (permRule == null) {
            if (isAjax) {
                return "fail";
            }
            return errorHandler.handleAccessDenied(request, response);
        }

        PropertiesConfiguration config;
        try {
            processUpdateRequest(request.getParameterMap());

            if (downloadToNolij()) {
                config = actionHelper.getEvalsConfig();
                String nolijDir = config.getString("pdf.nolijDir");
                String env = config.getString("pdf.env");
                GeneratePDF(appraisal, nolijDir, env, true);
            }

            if (appraisal.getRole().equals(ActionHelper.ROLE_SUPERVISOR)) {
                actionHelper.setupMyTeamActiveAppraisals();
            } else if (appraisal.getRole().equals(ActionHelper.ROLE_EMPLOYEE)) {
                actionHelper.setupMyAppraisals();
            }
        } catch (ModelException e) {
            SessionErrors.add(request, e.getMessage());
        }

        // If the user hit the save draft button, we stay in the same view
        if (request.getParameter("save-draft") != null || request.getParameter("cancel") != null) {
            if (request.getParameter("save-draft") != null) {
                SessionMessages.add(request, "draft-saved");
            }
            if (response instanceof ActionResponse) {
                ((ActionResponse) response).setWindowState(WindowState.MAXIMIZED);
            }

            // remove the object from session so that display picks up new assessment associations
            HibernateUtil.getCurrentSession().flush();
            HibernateUtil.getCurrentSession().clear();
            return display(request, response);
        }

        String status = appraisal.getStatus();
        String[] afterReviewStatus = {Appraisal.STATUS_RELEASE_DUE, Appraisal.STATUS_RELEASE_OVERDUE,
                Appraisal.STATUS_CLOSED};
        if (ArrayUtils.contains(afterReviewStatus, status) && isReviewer) {
            removeReviewAppraisalInSession();
        } else {
            updateAppraisalInSession();
        }

        if (isAjax) {
            return "success";
        }

        return homeAction.display(request, response);
    }

    /**
     * Whether or not the evaluation should be downloaded for nolij. If the appointment type is not prof. faculty
     * and the employee signed the evaluation, it is uploaded to nolij.
     *
     * @return
     */
    private boolean downloadToNolij() {
        if (appraisal.getAppointmentType().equals(AppointmentType.PROFESSIONAL_FACULTY)) {
            return false;
        }

        String signAppraisal = ParamUtil.getString(request, "sign-appraisal");
        return signAppraisal != null && !signAppraisal.equals("");
    }

    /**
     * Handles removing an appraisal from the reviewList stored in session. This method is called
     * by the AppraisalsAction.update method after a reviewer submits a review.
     *
     * @throws Exception
     */
    private void removeReviewAppraisalInSession() throws Exception {
        List<Appraisal> reviewList = actionHelper.getReviewsForLoggedInUser(-1);
        List<Appraisal> tempList = new ArrayList<Appraisal>();
        tempList.addAll(reviewList);
        for (Appraisal appraisalInSession: tempList) {
            if (appraisalInSession.getId() == appraisal.getId()) {
                reviewList.remove(appraisalInSession);
                break;
            }
        }

        PortletSession session = ActionHelper.getSession(request);
        session.setAttribute("reviewList", reviewList);
    }

    /**
     * Processes the processAction request (Map) and tries to save the appraisal. This method
     * moves the appraisal to the next appraisal step and sends any emails if necessary.
     *
     * @param requestMap
     * @throws Exception
     */
    private void processUpdateRequest(Map<String, String[]> requestMap) throws Exception {
        HashMap appraisalSteps = (HashMap) actionHelper.getPortletContextAttribute("appraisalSteps");
        MailerInterface mailer = (MailerInterface) actionHelper.getPortletContextAttribute("mailer");

        // set the overdue value before updating the status
        String beforeUpdateStatus = appraisal.getStatus();
        Integer oldOverdue = appraisal.getOverdue();

        // update appraisal & assessment fields based on permission rules
        initializeJSONData(requestMap);
        setAppraisalFields();

        boolean statusChanged = !appraisal.getStatus().equals(beforeUpdateStatus);
        if (statusChanged) {
            // Using the old status value call setStatusOverdue()
            String overdueMethod = StringUtils.capitalize(beforeUpdateStatus);
            overdueMethod = "set" + overdueMethod.replace("Due", "Overdue");
            try {
                // call setStageOverdue method
                Method controllerMethod = appraisal.getClass()
                        .getDeclaredMethod(overdueMethod, Integer.class);
                controllerMethod.invoke(appraisal, oldOverdue);
            } catch (NoSuchMethodException e) {
                // don't do anything since some methods might not exist.
            }

            appraisal.setOverdue(-999);
        }

        // save changes to db
        AppraisalMgr.updateAppraisal(appraisal, loggedInUser);

        // Send email if needed
        AppraisalStep appraisalStep;
        String employeeResponse = appraisal.getRebuttal();

        // If the employee signs and provides a rebuttal, we want to use a different
        // appraisal step so that we can send an email to the reviewer.
        if (submittedRebuttal(employeeResponse)) {
            String appraisalStepKey = "submit-response-" + appraisal.getAppointmentType();
            appraisalStep = (AppraisalStep) appraisalSteps.get(appraisalStepKey);
            // If the appointment type doesn't exist in the table, use "Default" type.
            if (appraisalStep == null) {
                appraisalStep = (AppraisalStep) appraisalSteps.get("submit-response-Default");
            }
        } else {
            appraisalStep = getAppraisalStep();
        }

        EmailType emailType = null;
        if (appraisalStep != null) {
            emailType = appraisalStep.getEmailType();
        }

        if (emailType != null) {
            mailer.sendMail(appraisal, emailType);
        }
    }

    /**
     * Takes the request map that we get from the user saving or submitting the evaluation form.
     * It parses the json data and sets up the structured json data to use. It also initializes
     * dbAssessmentsMap.
     *
     * @param requestMap
     */
    private void initializeJSONData(Map<String, String[]> requestMap) {
        String jsonText = "{}";
        if (requestMap.get("json_data") != null) {
            jsonText = requestMap.get("json_data")[0];
        }

        Gson gson = new Gson();
        jsonData = gson.fromJson(jsonText, AppraisalJSON.class);

        dbAssessmentsMap = appraisal.getAssessmentMap();
    }

    /**
     * Handles updating the appraisal fields in the appraisal and assessment objects.
     *
     */
    public void setAppraisalFields() throws Exception {
        String newStatus = null;
        Map<String, Boolean> dates = new HashMap<String, Boolean>();
        Map<String, Boolean> pidm = new HashMap<String, Boolean>();
        boolean clickedSubmitButton = jsonData.getButtonClicked().equals(permRule.getSubmit());

        if (permRule.canEdit("goalComments")) { // Save goalComments
            GoalVersion latestVersion = appraisal.getUnapprovedGoalsVersion();
            latestVersion.setGoalsComments(jsonData.getGoalsComments());
        }

        // updates goals, employee/supervisor results and handle new js goals
        setAssessmentFields();

        if (jsonData.getButtonClicked().equals("approve-goals")) {
            appraisal.getUnapprovedGoalsVersion().approveEmployeeGoals(loggedInUser.getId());
        }

        if (jsonData.getButtonClicked().equals("submit-goals")) {
            appraisal.getUnapprovedGoalsVersion().setGoalsSubmitDate(new Date());
        }

        if (permRule.canEdit("evaluation")) { // Save evaluation
            appraisal.setEvaluation(jsonData.getEvaluation());
            appraisal.setRating(jsonData.getRating());
            pidm.put("evaluator", clickedSubmitButton);
            dates.put("evaluationSubmitDate", clickedSubmitButton);

            if (appraisal.getIsSalaryUsed()) {
                saveRecommendedIncrease(jsonData);
            }
        }

        if (permRule.canEdit("review")) { // Save review
            appraisal.setReview(jsonData.getReview());
            pidm.put("reviewer", clickedSubmitButton);
            dates.put("reviewSubmitDate", clickedSubmitButton);
        }

        if (permRule.canEdit("employeeResponse")) { // Save employee response
            appraisal.setRebuttal(jsonData.getRebuttal());
            String employeeResponse = appraisal.getRebuttal();
            dates.put("rebuttalDate", submittedRebuttal(employeeResponse));
        }


        // Save the close out reason
        if (appraisal.getRole().equals(ActionHelper.ROLE_REVIEWER) || isAdminRole()) {
            if (jsonData.getCloseOutReasonId() != null) {
                CloseOutReason reason = CloseOutReasonMgr.get(jsonData.getCloseOutReasonId());
                appraisal.setCloseOutReason(reason);
                appraisal.setOriginalStatus(appraisal.getStatus());
                dates.put("closeOutDate", true);
                pidm.put("closeOutBy", true);
            }
        }

        // Approve/Deny Goals Reactivation
        if (permRule.getSubmit() != null && permRule.getSecondarySubmit() != null) {
            if (permRule.getSubmit().equals("approve-goals-reactivation") ||
                    permRule.getSecondarySubmit().equals("deny-goals-reactivation")) {
                reactivationGoals(jsonData.getButtonClicked());
            }
        }

        // If the appraisalStep object has a new status, update the appraisal object
        AppraisalStep appraisalStep = getAppraisalStep();
        if (appraisalStep != null) {
            newStatus = appraisalStep.getNewStatus();
        }

        if (newStatus != null && !newStatus.equals(appraisal.getStatus())) {
            appraisal.setStatus(newStatus);
            String employeeResponse = appraisal.getRebuttal();
            if (submittedRebuttal(employeeResponse)) {
                appraisal.setStatus(Appraisal.STATUS_REBUTTAL_READ_DUE);
            }
        }

        if(appraisal.getStatus().equals(Appraisal.STATUS_GOALS_REQUIRED_MODIFICATION)) {
            appraisal.getUnapprovedGoalsVersion().setGoalsRequiredModificationDate(new Date());
        }

        saveAppraisalMetadata(dates, pidm);
    }

    /**
     * Whether the role of the logged in user is either Master or Super Admin when viewing the appraisal
     * object.
     *
     * @return
     */
    private boolean isAdminRole() {
        return EvalsUtil.isOneOfAdminRoles(userRole);
    }

    /**
     * Sets the assessment fields (goal & results) based on the information that the user entered
     * in the form. It updates all assessments.
     *
     * @throws Exception
     */
    private void setAssessmentFields() throws Exception {
        if (jsonData.getAssessments() == null) { // if there are no assessments, exit
            return;
        }

        Assessment assessment;
        for (AssessmentJSON assessmentJSON : jsonData.getAssessments().values())   {
            assessment = dbAssessmentsMap.get(assessmentJSON.getId().toString());
            if (assessment != null) {
                if (permRule.canEdit("unapprovedGoals")) { // Save Goals
                    updateGoals(assessmentJSON, assessment, assessmentJSON.getDeleted().equals("1"));
                }

                // Save employee results
                if (permRule.canEdit("results") && assessmentJSON.getEmployeeResult() != null) {
                    assessment.setEmployeeResult(assessmentJSON.getEmployeeResult());
                }

                // Save supervisor results
                if (permRule.canEdit("supervisorResults") && assessmentJSON.getSupervisorResult() != null) {
                    assessment.setSupervisorResult(assessmentJSON.getSupervisorResult());
                }
            }
        }
    }

    /**
     * Saves metadata information in the Appraisal object. The metadata that we save is either
     * date values or PIDMs. Instead of calling each one of the setData or setPidm columns
     * indidivually, we use this method to call them.
     *
     * @param dates
     * @param pidm
     * @throws Exception
     */
    private void saveAppraisalMetadata(Map<String, Boolean> dates,
                                       Map<String, Boolean> pidm) throws Exception {
        // The pidms and dates fields that are set to true will get set by the for loops below.
        String buttonClicked = jsonData.getButtonClicked();
        dates.put("releaseDate", buttonClicked.equals("release-appraisal"));
        dates.put("resultSubmitDate", permRule.canEdit("results") && buttonClicked.equals("submit-results"));
        dates.put("supervisorRebuttalRead", permRule.canEdit("rebuttalRead") && buttonClicked.equals("read-appraisal-rebuttal"));
        dates.put("employeeSignedDate", permRule.canEdit("employeeResponse") && buttonClicked.equals("sign-appraisal"));

        for (String fieldName : dates.keySet()) {
            if (!dates.get(fieldName)) {
                continue;
            }

            String methodName = "set" + WordUtils.capitalize(fieldName);
            Method permissionMethod = appraisal.getClass().getDeclaredMethod(methodName, Date.class);
            permissionMethod.invoke(appraisal, new Date());
        }

        for (String fieldName : pidm.keySet()) {
            if (!pidm.get(fieldName)) {
                continue;
            }

            String methodName = "set" + WordUtils.capitalize(fieldName);
            Method permissionMethod = appraisal.getClass().getDeclaredMethod(methodName, Employee.class);
            permissionMethod.invoke(appraisal, loggedInUser);
        }
    }

    /**
     * Returns the sequence to assign to the next goal added via js.
     *
     * @param dbAssessmentsMap
     * @return
     */
    private Integer calculateAssessmentSequence(Map<String, Assessment> dbAssessmentsMap) {
        Integer nextSequence = 0;
        for (Map.Entry<String, Assessment> entry : dbAssessmentsMap.entrySet()) {
            Assessment assessment = entry.getValue();
            if (assessment.getSequence() > nextSequence) {
                nextSequence = assessment.getSequence();
            }
        }
        return nextSequence + 1;
    }

    /**
     * Handles approving/denying goals reactivation request.
     *
     * @param buttonClicked
     * @throws Exception
     */
    private void reactivationGoals(String buttonClicked) throws Exception {
        Boolean goalReactivationDecision = null;
        if (buttonClicked.equals("approve-goals-reactivation")) {
            goalReactivationDecision = true;
        } else if (buttonClicked.equals("deny-goals-reactivation")) {
            goalReactivationDecision = false;
        }

        GoalVersion unapprovedGoalsVersion = appraisal.getRequestPendingGoalsVersion();
        if (goalReactivationDecision != null && unapprovedGoalsVersion != null) {
            unapprovedGoalsVersion.setRequestDecisionPidm(loggedInUser.getId());
            unapprovedGoalsVersion.setRequestDecision(goalReactivationDecision);
            unapprovedGoalsVersion.setRequestDecisionDate(new Date());
            if (goalReactivationDecision) {
                AppraisalMgr.addAssessmentForGoalsReactivation(unapprovedGoalsVersion, appraisal);
            }
        }
    }


    /**
     * Updates the goals text and criteria checkboxes that the user entered in the form.
     *
     * @param assessmentJSON
     * @param assessment
     * @param deleted
     */
    private void updateGoals(AssessmentJSON assessmentJSON, Assessment assessment, Boolean deleted) {
        if (appraisal.getUnapprovedGoalsVersion() == null) {
            return; // if there isn't an unapproved goals versions exit
        }

        if (deleted) { // Save the deleted flag if present
            assessment.setDeleteDate(new Date());
            assessment.setDeleterPidm(loggedInUser.getId());
        }

        // Save goal
        if (assessmentJSON.getGoal() != null) {
            assessment.setGoal(assessmentJSON.getGoal());
        }

        // Save criteria checkboxes
        Integer checkboxIndex = 1;
        LinkedHashMap criteriaMap = (LinkedHashMap) assessmentJSON.getCriteria();
        for (AssessmentCriteria assessmentCriteria : assessment.getSortedAssessmentCriteria()) {
            Integer id = assessmentCriteria.getId();
            if (id == null) {
                id = checkboxIndex;
                checkboxIndex++;
            }
            boolean checked = (Boolean) criteriaMap.get(id);
            assessmentCriteria.setChecked(checked);
        }
    }

    /**
     * Creates a single new assessment and returns its id. Called by an ajax call when a user
     * clicks on the "Add Goal" button.
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String addAssessment(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);
        initializeJSONData(request.getParameterMap());
        Session session = HibernateUtil.getCurrentSession();
        // Get assessmentCriteria
        Assessment assessment = dbAssessmentsMap.entrySet().iterator().next().getValue();
        List<AssessmentCriteria> sortedAssessmentCriteria = assessment.getSortedAssessmentCriteria();
        List<CriterionArea> criterionAreas = new ArrayList<CriterionArea>();
        for (AssessmentCriteria assessmentCriteria : sortedAssessmentCriteria) {
            criterionAreas.add(assessmentCriteria.getCriteriaArea());
        }
        // Get goalVersion
        GoalVersion reactivatedGoalVersion = appraisal.getReactivatedGoalVersion();
        if (reactivatedGoalVersion == null) {
            reactivatedGoalVersion = (GoalVersion) appraisal.getGoalVersions().toArray()[0];
        }
        // Create new assessment
        Integer nextSequence = calculateAssessmentSequence(dbAssessmentsMap);
        assessment = AppraisalMgr.createNewAssessment(reactivatedGoalVersion, nextSequence, criterionAreas);
        session.save(assessment);
        return "{id:" + assessment.getId() + ", status:\"success\"}";
    }


    /**
     * Saves the rating on the salary object based on the rating the user selected:
     * Rating 1 -   the user can specify a value within a range (min & max values are in configuration
     *              table. If the current salary is at the top of the pay range, the increase is set to 0.
     * Rating 2 -   the increase is set automatically by a configuration value
     * Rating 3 -   the increase is set to 0
     *
     * The allowed range for rating 1 and fixed value for rating 2 depend on whether or not the
     * current salary is above or below the control point.
     *
     * @param jsonData
     */
    private void saveRecommendedIncrease(AppraisalJSON jsonData) throws ModelException {
        // get the salary validation values. They change depending on whether current salary is
        // above or below the midpoint
        Map<String, String> salaryValidationValues = getSalaryValidationValues();
        Double increaseRate2Value = Double.parseDouble(salaryValidationValues.get("increaseRate2Value"));
        Double increaseRate1MinVal = Double.parseDouble(salaryValidationValues.get("increaseRate1MinVal"));
        Double increaseRate1MaxVal= Double.parseDouble(salaryValidationValues.get("increaseRate1MaxVal"));

        Salary salary = appraisal.getSalary();
        Double increaseValue = 0d;
        if (appraisal.getRating() != null) {
            if (appraisal.getRating() == 1) {
                // can only specify an increase if the salary is not at the top pay range
                if (salary.getCurrent() < salary.getHigh()) {
                    String salaryRecommendation = jsonData.getSalaryRecommendation();
                    // Check that the user submitted a valid salary increase
                    if (salaryRecommendation == null || !NumberUtils.isNumber(salaryRecommendation)) {
                        return;
                    }

                    Double submittedIncrease = Double.parseDouble(salaryRecommendation);
                    if (submittedIncrease >= increaseRate1MinVal && submittedIncrease <= increaseRate1MaxVal) {
                        increaseValue = submittedIncrease;
                    } else {
                        throw new ModelException(resource.getString("appraisal-salary-increase-error-invalid-change"));
                    }
                }
            } else if (appraisal.getRating() == 2) {
                increaseValue = increaseRate2Value;
            }
        }

        salary.setIncrease(increaseValue);
    }

    /**
     * Figures out the appraisal step key for the button that the user pressed when the appraisal
     * form was submitted.
     *
     * @return
     */
    private AppraisalStep getAppraisalStep() {
        HashMap appraisalSteps = (HashMap) actionHelper.getPortletContextAttribute("appraisalSteps");
        AppraisalStep appraisalStep;
        String appraisalStepKey;
        ArrayList<String> appraisalButtons = new ArrayList<String>();
        if (permRule.getSaveDraft() != null) {
            appraisalButtons.add(permRule.getSaveDraft());
        }
        if (permRule.getSecondarySubmit() != null) {
            appraisalButtons.add(permRule.getSecondarySubmit());
        }
        if (permRule.getSubmit() != null) {
            appraisalButtons.add(permRule.getSubmit());
        }
        // close out button
        appraisalButtons.add("close-appraisal");

        for (String button : appraisalButtons) {
            // If this button is the one the user clicked, use it to look up the
            // appraisalStepKey
            if (jsonData.getButtonClicked().equals(button)) {
                appraisalStepKey = button + "-" + appraisal.getAppointmentType();
                appraisalStep = (AppraisalStep) appraisalSteps.get(appraisalStepKey);
                // If the appointment type doesn't exist in the table, use "Default" type.
                if (appraisalStep == null) {
                    return (AppraisalStep) appraisalSteps.get(button + "-" + "Default");
                }
                return appraisalStep;
            }
        }

        return null;
    }

    private String GeneratePDF(Appraisal appraisal, String dirName, String env,
                               boolean  insertRecordIntoTable) throws Exception {
        // Create PDF
        String rootDir = actionHelper.getPortletContext().getRealPath("/");
        Map<String, List<Rating>> ratingsMap = (HashMap) actionHelper.getPortletContext().getAttribute("ratings");
        List<Rating> ratings = RatingMgr.getRatings(ratingsMap, appraisal.getAppointmentType());
        EvalsPDF PdfGenerator = new EvalsPDF(rootDir, appraisal, resource, dirName, env, ratings);
        String filename = PdfGenerator.createPDF();

        // Insert a record into the nolij_copies table
        if (insertRecordIntoTable) {
            String onlyFilename = filename.replaceFirst(dirName, "");
            NolijCopyMgr.add(appraisal.getId(), onlyFilename);
        }

        return filename;
    }

    /**
     * Specifies whether or not the employee submitted a rebuttal when the appraisal was signed.
     *
     * @param employeeResponse
     * @return
     */
    private boolean submittedRebuttal(String employeeResponse) {
        return jsonData.getButtonClicked().equals("sign-appraisal") &&
                employeeResponse != null && !employeeResponse.equals("");
    }


    /**
     * Allows the end user to download a PDF copy of the appraisal
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String downloadPDF(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);

        // Check to see if the logged in user has permission to access the appraisal
        if (permRule == null) {
            return errorHandler.handleAccessDenied(request, response);
        }

        // 2) Compose a file name
        PropertiesConfiguration config = actionHelper.getEvalsConfig();
        String tmpDir = config.getString("pdf.tmpDir");

        // 2) Create PDF
        String filename = GeneratePDF(appraisal, tmpDir, "dev2", false);

        // 3) Read the PDF file and provide to the user as attachment
        if (response instanceof ResourceResponse) {
            String title = appraisal.getJob().getJobTitle().replace(" ", "_");
            String employeeName = appraisal.getJob().getEmployee().getName().replace(" ", "_");
            String downloadFilename = "performance-appraisal-"+ title + "-" +
                     employeeName + "-" + appraisal.getJob().getPositionNumber()
                    + ".pdf";
            ResourceResponse res = (ResourceResponse) response;
            res.setContentType("application/pdf");
            res.addProperty(HttpHeaders.CACHE_CONTROL, "max-age=3600, must-revalidate");
            res.addProperty(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+downloadFilename+"\"");

            OutputStream out = res.getPortletOutputStream();
            RandomAccessFile in = new RandomAccessFile(filename, "r");

            byte[] buffer = new byte[4096];
            int len;
            while((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            out.flush();
            in.close();
            out.close();

            // 4) Delete the temp PDF file generated
            File pdfFile = new File(filename);
            pdfFile.delete();
        }
        return null;
    }

    /***
     * This method updates the status of the appraisal in myTeam or myStatus to reflect the
     * changes from the update method.
     *
     * @throws Exception
     */
    private void updateAppraisalInSession() throws Exception {
        List<Appraisal>  appraisals;

        if (appraisal.getRole().equals("employee")) {
            appraisals = actionHelper.getMyAppraisals();
        } else if (appraisal.getRole().equals(ActionHelper.ROLE_SUPERVISOR)) {
            appraisals = actionHelper.getMyTeamActiveAppraisals();
        } else {
            return;
        }

        for (Appraisal appraisalInSession: appraisals) {
            if (appraisalInSession.getId() == appraisal.getId()) {
                appraisalInSession.setStatus(appraisal.getStatus());
                break;
            }
        }
    }

    /**
     * Sends the appraisal to NOLIJ. This is only allowed to reviewers and does not check whether or not
     * the appraisal has been sent to nolij before. It calls createNolijPDF to do the work.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String resendAppraisalToNolij(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);

        boolean canSendToNolij = (permRule.getSendToNolij() == null ? false : permRule.getSendToNolij().equals("y"));

        actionHelper.addToRequestMap("id", appraisal.getId());

        if (!canSendToNolij) {
            String errorMsg = resource.getString("appraisal-resend-permission-denied");
            actionHelper.addErrorsToRequest(errorMsg);
            return display(request, response);
        }

        // If there is a problem, createNolijPDF will throw an exception
        PropertiesConfiguration config = actionHelper.getEvalsConfig();
        String nolijDir = config.getString("pdf.nolijDir");
        String env = config.getString("pdf.env");
        GeneratePDF(appraisal, nolijDir, env, true);

        SessionMessages.add(request, "appraisal-sent-to-nolij-success");

        return display(request, response);
    }

    /**
     * Handles an admin/reviewer closing an appraisal. We only display the form to close it. The
     * logic to handle closing is done by update method.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String closeOutAppraisal(PortletRequest request, PortletResponse response) throws Exception {
        initialize(request);

        // Check to see if the logged in user has permission to access the appraisal
        boolean isAdminOrReviewer = isAdminRole() || userRole.equals(ActionHelper.ROLE_REVIEWER);
        if (permRule == null || !isAdminOrReviewer) {
            return errorHandler.handleAccessDenied(request, response);
        }

        List<CloseOutReason> reasonList = CloseOutReasonMgr.list(false);
        appraisal.getJob().getEmployee().toString();

        actionHelper.addToRequestMap("reasonsList", reasonList);
        actionHelper.addToRequestMap("appraisal", appraisal);
        actionHelper.useMaximizedMenu();

        return Constants.JSP_APPRAISAL_CLOSEOUT;
    }

    /**
     * Handles setting the status of an appraisal record to results due.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String setStatusToResultsDue(PortletRequest request, PortletResponse response)
            throws Exception {
        initialize(request);

        if (!isAdminRole() && !userRole.equals(ActionHelper.ROLE_REVIEWER)) {
            return errorHandler.handleAccessDenied(request, response);
        }

        if (request instanceof ActionRequest && response instanceof ActionResponse) {
            appraisal.setOriginalStatus(appraisal.getStatus());
            appraisal.setStatus(Appraisal.STATUS_RESULTS_DUE);
            AppraisalMgr.updateAppraisalStatus(appraisal);
            SessionMessages.add(request, "appraisal-set-status-success");
            return display(request, response);
        }

        return homeAction.display(request, response);
    }

    public String requestGoalsReactivation(PortletRequest request, PortletResponse response)
            throws Exception{
        initialize(request);

        // Check to see if the logged in user has permission to access the appraisal
        // Check that user making request is employee & status is goals approved
        if (permRule == null || !userRole.equals(ActionHelper.ROLE_EMPLOYEE) ||
                !appraisal.getStatus().equals(Appraisal.STATUS_GOALS_APPROVED)) {
            return errorHandler.handleAccessDenied(request, response);
        }

        HashMap<String,AppraisalStep> appraisalSteps =
                (HashMap) actionHelper.getPortletContextAttribute("appraisalSteps");
        AppraisalStep appraisalStep = appraisalSteps.get("request-goals-reactivation-Default");

        // update status
        appraisal.setOriginalStatus(appraisal.getStatus());
        appraisal.setStatus(appraisalStep.getNewStatus());
        AppraisalMgr.updateAppraisalStatus(appraisal);
        SessionMessages.add(request, "appraisal-goals-reactivation-requested");

        // send email to supervisor
        MailerInterface mailer = (MailerInterface) actionHelper.getPortletContextAttribute("mailer");
        EmailType emailType = appraisalStep.getEmailType();
        mailer.sendMail(appraisal, emailType); // relies on status so, we need status set first

        // create goalVersion pojo && associate it
        AppraisalMgr.addGoalVersion(appraisal);

        // update status of cached appraisal object
        updateAppraisalInSession();

        return display(request, response);
    }

    /**
     * Displays page for the supervisor to initiate the professional faculty evaluations. A message is displayed
     * to the supervisor, a list of employees with and without evaluations. The supervisor selects a cycle to start
     * the evaluations.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String initiateProfessionalFacultyEvals(PortletRequest request, PortletResponse response)
            throws Exception {
        initialize(request);
        List<Job> shortJobsWithEvals = new ArrayList<Job>();
        List<Job> shortJobsWithOutEvals = new ArrayList<Job>();

        if (!getProfFacultyInitiateData(shortJobsWithEvals, shortJobsWithOutEvals)) {
            return errorHandler.handleAccessDenied(request, response);
        }

        actionHelper.addToRequestMap("shortJobsWithEvals", shortJobsWithEvals);
        actionHelper.addToRequestMap("jobsWithoutEvals", shortJobsWithOutEvals);
        actionHelper.addToRequestMap("months", CWSUtil.getMonthsInYear("MMM"));

        ArrayList<String> years = new ArrayList<String>();
        for (int i = -1; i <= 1; i++) {
            years.add(new DateTime().plusYears(i).toString("YYYY"));
        }
        actionHelper.addToRequestMap("years", years);
        actionHelper.useMaximizedMenu();
        return Constants.JSP_INITIATE_PROFESSIONAL_FACULTY;
    }

    /**
     * Gathers the professional faculty data employees with and without evaluations. It adds the short objects
     * with only the needed information to the two lists passed in as parameters. This method returns false in
     * several scenarios: when the user is not a supervisor or when none of the employees are professional faculty
     * or when the employees don't need their evaluation record created. The reasoning behind returning false is that
     * the only way this method gets executed is if a supervisor clicked the initiate button or if the user is
     * trying to hack the url. That's why the calling method uses an access denied error message.
     *
     * @param shortJobsWithEvals
     * @param shortJobsWithOutEvals
     * @return
     * @throws Exception
     */
    private Boolean getProfFacultyInitiateData(List<Job> shortJobsWithEvals, List<Job> shortJobsWithOutEvals)
            throws Exception {
        List<String> appointmentTypes = new ArrayList<String>();
        appointmentTypes.add(AppointmentType.PROFESSIONAL_FACULTY);

        List<Job> supervisorJobs = JobMgr.getSupervisorJobs(loggedInUser);
        // check that the user holds at least 1 supervising job
        if (supervisorJobs == null || supervisorJobs.isEmpty()) {
            return false;
        }

        List<Job> employeeShortJobs = JobMgr.listEmployeesShortJobs(supervisorJobs, appointmentTypes);
        shortJobsWithOutEvals.addAll(JobMgr.getJobWithoutActiveEvaluations(employeeShortJobs));

        // Check that the supervisor has jobs that need to be initiated in EvalS
        if (shortJobsWithOutEvals == null || shortJobsWithOutEvals.isEmpty()) {
            return false;
        }

        shortJobsWithEvals.addAll(employeeShortJobs);
        shortJobsWithEvals.removeAll(shortJobsWithOutEvals);

        // iterate over the objects so that we get the employee name to prevent jsp lazy loading exception
        for (Job job : shortJobsWithEvals) {
            job.getEmployee().getName();
            job.getSupervisor().getPositionNumber();
            ((Appraisal) job.getAppraisals().iterator().next()).getReviewPeriod();
        }
        for (Job job : shortJobsWithOutEvals) {
            job.getEmployee().getName();
            job.getSupervisor().getPositionNumber();
        }

        // If we got here, there was no access denied error
        return true;
    }

    /**
     * Handles processing the cycle information provided by the supervisor and creates the evaluations for
     * professional faculty employees. It sends an email about the created evaluations and logs the information.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String createProfFacultyEvaluations(PortletRequest request, PortletResponse response) throws Exception {
        // get form data and user info
        initialize(request);
        Integer year = ParamUtil.getInteger(request, "year");
        Integer month = ParamUtil.getInteger(request, "month");
        DateTime startDate = new DateTime().withDate(year, month, 1);

        // Logging information
        EvalsLogger logger = (EvalsLogger) actionHelper.getPortletContextAttribute("log");
        String loggingMsg = "supervisor.pidm = " + loggedInUser.getId() + ", evaluation cycle = "
                + startDate.toString(Constants.DATE_FORMAT_FULL);

        // create evaluations
        List<Appraisal> newAppraisals = AppraisalMgr.createProfessionalFacultyEvals(loggedInUser, startDate);
        if (newAppraisals == null || newAppraisals.isEmpty()) {
            actionHelper.addErrorsToRequest(resource.getString("prof-faculty-create-evals-error"));
            logger.log(Logger.ERROR, "Failed to create professional faculty evaluations", loggingMsg);
            return initiateProfessionalFacultyEvals(request, response);
        }

        loggingMsg += "\nEvaluations created for the jobs listed below:";
        for (Appraisal newAppraisal : newAppraisals) {
            loggingMsg += "\n" + newAppraisal.getJob().getSignature() + " appraisal id = " + newAppraisal.getId();
        }
        logger.log(Logger.INFORMATIONAL, "Initiated professional faculty evaluations", loggingMsg);
        SessionMessages.add(request, "prof-faculty-create-evals-success");

        notifyEmployeesOfInitiatedEvaluation(newAppraisals);

        // refresh cached list of evaluations in supervisor home view
        List<Appraisal> appraisals = actionHelper.getMyTeamActiveAppraisals();
        appraisals.addAll(newAppraisals);

        return homeAction.display(request, response);
    }

    /**
     * Sends email to employee to let them know that the evaluation has been created by the supervisor.
     *
     * @param appraisals
     * @throws Exception
     */
    private void notifyEmployeesOfInitiatedEvaluation(List<Appraisal> appraisals) throws Exception {
        MailerInterface mailer = (Mailer) actionHelper.getPortletContextAttribute("mailer");
        Map<String, EmailType> emailTypeMap = EmailTypeMgr.getMap();
        EmailType emailType = emailTypeMap.get("initiatedProfessionalFaculty");

        for (Appraisal newAppraisal : appraisals) {
            mailer.sendMail(newAppraisal, emailType);
        }
    }

    public void setActionHelper(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
    }

    public void setHomeAction(HomeAction homeAction) {
        this.homeAction = homeAction;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

}
