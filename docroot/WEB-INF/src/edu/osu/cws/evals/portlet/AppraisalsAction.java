package edu.osu.cws.evals.portlet;

import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.ParamUtil;
import edu.osu.cws.evals.hibernate.AppraisalMgr;
import edu.osu.cws.evals.hibernate.CloseOutReasonMgr;
import edu.osu.cws.evals.hibernate.JobMgr;
import edu.osu.cws.evals.hibernate.NolijCopyMgr;
import edu.osu.cws.evals.models.*;
import edu.osu.cws.evals.util.EvalsPDF;
import edu.osu.cws.evals.util.HibernateUtil;
import edu.osu.cws.evals.util.MailerInterface;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

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
    private void initialize(PortletRequest request) throws Exception {
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
                setAppraisalPermissionRule();
                appraisal.setRole(userRole);
                appraisal.setPermissionRule(permRule);
            }
        }
    }

    /**
     * Figures out the current user role in the appraisal and returns the respective permission
     * rule for that user role and action in the appraisal.
     *
     * @throws Exception
     */
    private void setAppraisalPermissionRule() throws Exception {
        HashMap permissionRules =
                (HashMap) actionHelper.getPortletContext().getAttribute("permissionRules");
        permRule =
                (PermissionRule) permissionRules.get(appraisal.getStatus() + "-" + userRole);
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

        if (JobMgr.isUpperSupervisor(appraisal.getJob(), pidm)) {
            return ActionHelper.ROLE_UPPER_SUPERVISOR;
        }

        if (actionHelper.getAdmin() != null) {
            return ActionHelper.ROLE_ADMINISTRATOR;
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
                ArrayList<Appraisal> reviews = actionHelper.getReviewsForLoggedInUser(-1);
                actionHelper.addToRequestMap("pendingReviews", reviews);
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

        actionHelper.addToRequestMap("appraisal", appraisal);
        actionHelper.addToRequestMap("permissionRule", permRule);
        actionHelper.useMaximizedMenu();

        if (appraisal.getJob().getAppointmentType().equals(AppointmentType.CLASSIFIED_IT)) {
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
        String aboveOrBelow = "below";
        if (appraisal.getSalary().getCurrent() > appraisal.getSalary().getMidPoint()) {
            aboveOrBelow = "above";
        }

        Map<String, Configuration> configurationMap =
                (Map<String, Configuration>) actionHelper.getPortletContextAttribute("configurations");
        String increaseRate2Value = configurationMap.get("IT-increase-rate2-" + aboveOrBelow + "-control-value").getValue();
        String increaseRate1MinVal = configurationMap.get("IT-increase-rate1-" + aboveOrBelow + "-control-min-value").getValue();
        String increaseRate1MaxVal= configurationMap.get("IT-increase-rate1-" + aboveOrBelow + "-control-max-value").getValue();

        salaryValidationValues.put("increaseRate2Value", increaseRate2Value);
        salaryValidationValues.put("increaseRate1MinVal", increaseRate1MinVal);
        salaryValidationValues.put("increaseRate1MaxVal", increaseRate1MaxVal);

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
        if (permRule == null) {
            return errorHandler.handleAccessDenied(request, response);
        }

        PropertiesConfiguration config;
        try {
            processUpdateRequest(request.getParameterMap());

            String signAppraisal = ParamUtil.getString(request, "sign-appraisal");
            if (signAppraisal != null && !signAppraisal.equals("")) {
                config = actionHelper.getEvalsConfig();
                String nolijDir = config.getString("pdf.nolijDir");
                String env = config.getString("pdf.env");
                GeneratePDF(appraisal, nolijDir, env, true);
            }

            if (appraisal.getRole().equals(ActionHelper.ROLE_SUPERVISOR)) {
                actionHelper.setupMyTeamActiveAppraisals();
            } else if (appraisal.getRole().equals(ActionHelper.ROLE_EMPLOYEE)) {
                actionHelper.setupMyActiveAppraisals();
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

        return homeAction.display(request, response);
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

        PortletSession session = request.getPortletSession(true);
        session.setAttribute("reviewList", reviewList);
    }

    /**
     * Processes the processAction request (Map) and tries to save the appraisal. This method
     * moves the appraisal to the next appraisal step and sends any emails if necessary.
     *
     * @param requestMap
     * @throws Exception
     */
    private void processUpdateRequest(Map requestMap) throws Exception {
        HashMap appraisalSteps = (HashMap) actionHelper.getPortletContextAttribute("appraisalSteps");
        MailerInterface mailer = (MailerInterface) actionHelper.getPortletContextAttribute("mailer");

        // set the overdue value before updating the status
        String beforeUpdateStatus = appraisal.getStatus();
        Integer oldOverdue = appraisal.getOverdue();

        // update appraisal & assessment fields based on permission rules
        setAppraisalFields(requestMap);

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
        String appointmentType = appraisal.getJob().getAppointmentType();
        AppraisalStep appraisalStep;
        String employeeResponse = appraisal.getRebuttal();

        // If the employee signs and provides a rebuttal, we want to use a different
        // appraisal step so that we can send an email to the reviewer.
        if (submittedRebuttal(requestMap, employeeResponse)) {
            String appraisalStepKey = "submit-response-" + appointmentType;
            appraisalStep = (AppraisalStep) appraisalSteps.get(appraisalStepKey);
            // If the appointment type doesn't exist in the table, use "Default" type.
            if (appraisalStep == null) {
                appraisalStep = (AppraisalStep) appraisalSteps.get("submit-response-Default");
            }
        } else {
            appraisalStep = getAppraisalStep(requestMap, appointmentType);
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
     * Handles updating the appraisal fields in the appraisal and assessment objects.
     *
     * @param requestMap
     */
    private void setAppraisalFields(Map<String, String[]> requestMap) throws Exception {
        String parameterKey = "";

        // Save Goals
        if (permRule.getUnapprovedGoals() != null && permRule.getUnapprovedGoals().equals("e")) {
            updateGoals(requestMap);

        }
        // Save goalComments
        if (permRule.getGoalComments() != null && permRule.getGoalComments().equals("e")) {
            if (requestMap.get("appraisal.goalsComments") != null) {
                appraisal.setGoalsComments(requestMap.get("appraisal.goalsComments")[0]);
            }
        }
        // Save employee results
        if (permRule.getResults() != null && permRule.getResults().equals("e")) {
            for (GoalVersion goalVersion : appraisal.getGoalVersions()) {
                for (Assessment assessment : goalVersion.getAssessments()) {
                    String assessmentID = Integer.toString(assessment.getId());
                    parameterKey = "assessment.employeeResult." + assessmentID;
                    if (requestMap.get(parameterKey) != null) {
                        assessment.setEmployeeResult(requestMap.get(parameterKey)[0]);
                    }
                }
            }
        }
        // Save Supervisor Results
        if (permRule.getSupervisorResults() != null && permRule.getSupervisorResults().equals("e")) {
            for (GoalVersion goalVersion : appraisal.getGoalVersions()) {
                for (Assessment assessment : goalVersion.getAssessments()) {
                    String assessmentID = Integer.toString(assessment.getId());
                    parameterKey = "assessment.supervisorResult." + assessmentID;
                    if (requestMap.get(parameterKey) != null) {
                        assessment.setSupervisorResult(requestMap.get(parameterKey)[0]);
                    }
                }
            }
        }
        if (requestMap.get("submit-results") != null) {
            appraisal.setResultSubmitDate(new Date());
        }
        // Save evaluation
        if (permRule.getEvaluation() != null && permRule.getEvaluation().equals("e")) {
            if (requestMap.get("appraisal.evaluation") != null) {
                appraisal.setEvaluation(requestMap.get("appraisal.evaluation")[0]);
            }
            if (requestMap.get("appraisal.rating") != null) {
                appraisal.setRating(Integer.parseInt(requestMap.get("appraisal.rating")[0]));
            }
            if (requestMap.get(permRule.getSubmit()) != null) {
                appraisal.setEvaluationSubmitDate(new Date());
                appraisal.setEvaluator(loggedInUser);
            }

            if (appraisal.getJob().getAppointmentType().equals(AppointmentType.CLASSIFIED_IT)) {
                saveRecommendedIncrease(requestMap);
            }
        }
        // Save review
        if (permRule.getReview() != null && permRule.getReview().equals("e")) {
            if (requestMap.get("appraisal.review") != null) {
                appraisal.setReview(requestMap.get("appraisal.review")[0]);
            }
            if (requestMap.get(permRule.getSubmit()) != null) {
                appraisal.setReviewer(loggedInUser);
                appraisal.setReviewSubmitDate(new Date());
            }
        }
        if (requestMap.get("sign-appraisal") != null) {
            appraisal.setEmployeeSignedDate(new Date());
        }
        if (requestMap.get("release-appraisal") != null) {
            appraisal.setReleaseDate(new Date());
        }
        // Save employee response
        if (permRule.getEmployeeResponse() != null && permRule.getEmployeeResponse().equals("e")) {
            appraisal.setRebuttal(requestMap.get("appraisal.rebuttal")[0]);
            String employeeResponse = appraisal.getRebuttal();
            if (submittedRebuttal(requestMap, employeeResponse)) {
                appraisal.setRebuttalDate(new Date());
            }
        }
        // Save supervisor rebuttal read
        if (permRule.getRebuttalRead() != null && permRule.getRebuttalRead().equals("e")
                && requestMap.get("read-appraisal-rebuttal") != null) {
            appraisal.setSupervisorRebuttalRead(new Date());
        }

        // Save the close out reason
        if (appraisal.getRole().equals(ActionHelper.ROLE_REVIEWER) ||
                appraisal.getRole().equals(ActionHelper.ROLE_ADMINISTRATOR )) {
            if (requestMap.get("appraisal.closeOutReasonId") != null) {
                int closeOutReasonId = Integer.parseInt(requestMap.get("appraisal.closeOutReasonId")[0]);
                CloseOutReason reason = CloseOutReasonMgr.get(closeOutReasonId);

                appraisal.setCloseOutBy(loggedInUser);
                appraisal.setCloseOutDate(new Date());
                appraisal.setCloseOutReason(reason);
                appraisal.setOriginalStatus(appraisal.getStatus());
            }
        }

        // Approve/Deny Goals Reactivation
        if (permRule.getSubmit() != null && permRule.getSecondarySubmit() != null) {
            if (permRule.getSubmit().equals("approve-goals-reactivation") ||
                    permRule.getSecondarySubmit().equals("deny-goals-reactivation")) {
                reactivationGoals(requestMap);
            }
        }

        // If the appraisalStep object has a new status, update the appraisal object
        String appointmentType = appraisal.getJob().getAppointmentType();
        AppraisalStep appraisalStep = getAppraisalStep(requestMap, appointmentType);
        String newStatus = null;
        if (appraisalStep != null) {
            newStatus = appraisalStep.getNewStatus();
        }

        if (newStatus != null && !newStatus.equals(appraisal.getStatus())) {
            appraisal.setStatus(newStatus);
            String employeeResponse = appraisal.getRebuttal();
            if (submittedRebuttal(requestMap, employeeResponse)) {
                appraisal.setStatus(Appraisal.STATUS_REBUTTAL_READ_DUE);
            }
        }
        if (appraisal.getStatus().equals(Appraisal.STATUS_GOALS_REQUIRED_MODIFICATION)) {
            appraisal.setGoalsRequiredModificationDate(new Date());
        }
    }

    /**
     * Handles approving/denying goals reactivation request.
     *
     * @param requestMap
     * @throws Exception
     */
    private void reactivationGoals(Map<String, String[]> requestMap) throws Exception {
        Boolean goalReactivationDecision = null;
        if (requestMap.get("approve-goals-reactivation") != null) {
            goalReactivationDecision = true;
        } else if (requestMap.get("deny-goals-reactivation") != null) {
            goalReactivationDecision = false;
        }

        GoalVersion unapprovedGoalsVersion = appraisal.getRequestPendingGoalsVersion();
        if (goalReactivationDecision != null && unapprovedGoalsVersion != null) {
            unapprovedGoalsVersion.setRequestDecisionPidm(loggedInUser.getId());
            unapprovedGoalsVersion.setRequestDecision(goalReactivationDecision);
            if (goalReactivationDecision) {
                AppraisalMgr.addAssessmentForGoalsReactivation(unapprovedGoalsVersion, appraisal);
            }
        }
    }

    /**
     * Handles updating the goals. Sets the goals, and assessment criteria. Adds/Removes assessments
     * if the user did so in the html form.
     *
     * @param requestMap
     */
    private void updateGoals(Map<String, String[]> requestMap) {
        String parameterKey;
        // if there isn't an unapproved goals versions exit
        if (appraisal.getUnapprovedGoalsVersion() == null) {
            return;
        }

        // The order is important since we'll append at the end the new assessments
        List<Assessment> assessments = appraisal.getUnapprovedGoalsVersion().getSortedAssessments();
        int oldAssessmentTotal = assessments.size();
        Map<Integer, String> sequenceToFormIndex = addNewAssessments(requestMap, assessments);


        int assessmentFormIndex = 0;
        Collections.sort(assessments);
        for (Assessment assessment : assessments) {
            String assessmentID = Integer.toString(assessment.getId());

            // catch any newly added assignments, where the assessmentId is different.
            assessmentFormIndex++;
            String formIndex = sequenceToFormIndex.get(assessment.getSequence());
            if (assessmentFormIndex > oldAssessmentTotal) {
                // For newly added assessments, the formIndex is used instead of assessment id
                // formIndex is used since one of the newly added assessments could have been
                // deleted before the form was submitted.
                assessmentID = formIndex;
            }
            parameterKey = "appraisal.goal." + assessmentID;
            if (requestMap.get(parameterKey) != null) {
                assessment.setGoal(requestMap.get(parameterKey)[0]);
            }
            updateAssessmentCriteria(requestMap, oldAssessmentTotal, assessmentFormIndex, assessment, formIndex);

            // Save the deleted flag if present
            parameterKey = "appraisal.assessment.deleted." + assessmentID;
            String[] deletedFlag = requestMap.get(parameterKey);
            if (deletedFlag != null && deletedFlag[0].equals("1")) {
                assessment.setDeleteDate(new Date());
                assessment.setDeleterPidm(loggedInUser.getId());
            }
        }
        if (requestMap.get("submit-goals") != null) {
            appraisal.setGoalsSubmitDate(new Date());
        }
        if (requestMap.get("approve-goals") != null) {
            appraisal.getUnapprovedGoalsVersion().setGoalsApprovedDate(new Date());
            appraisal.getUnapprovedGoalsVersion().setGoalsApprovedPidm(loggedInUser.getId());
        }
    }

    /**
     * Handles updating the assessment criteria checkboxes.
     *
     * @param requestMap
     * @param oldAssessmentTotal
     * @param assessmentFormIndex
     * @param assessment
     * @param formIndex
     */
    private void updateAssessmentCriteria(Map<String, String[]> requestMap, int oldAssessmentTotal,
                                          int assessmentFormIndex, Assessment assessment, String formIndex) {
        String parameterKey;// Save the assessment criteria for each assessment.
        int assessmentCriteriaFormIndex = 0; // used to calculate id of newly added assessment criteria
        for (AssessmentCriteria assessmentCriteria : assessment.getSortedAssessmentCriteria()) {
            assessmentCriteriaFormIndex++;
            int suffix = assessmentCriteria.getId();
            if (assessmentFormIndex > oldAssessmentTotal) {
                // For newly added assessments, the formIndex is used as the base for
                // assessment criteria ids.
                suffix = Integer.parseInt(formIndex) * assessmentCriteriaFormIndex;
            }
            parameterKey = "appraisal.assessmentCriteria." + suffix;
            if (requestMap.get(parameterKey) != null) {
                assessmentCriteria.setChecked(true);
            } else {
                assessmentCriteria.setChecked(false);
            }
        }
    }

    /**
     * Handles adding new assessments that were added to an appraisal via JS. The new assessment
     * objects are saved along with their sequence, creator pidm and date.
     *
     * @param requestMap
     * @param assessments               List of original non-deleted assessments
     * @return
     */
    private Map<Integer, String> addNewAssessments(Map<String, String[]> requestMap,
                                                   List<Assessment> assessments) {
        String parameterKey;// map used to get the form indexed based on the assessment sequence
        int oldAssessmentTotal = assessments.size();
        Map<Integer, String> sequenceToFormIndex = new HashMap<Integer, String>();

        // begin adding new goals!!!
        Integer numberOfAssessmentsAdded = 0;
        if (requestMap.get("assessmentCount") != null) {
            Integer newAssessmentTotal = Integer.parseInt(requestMap.get("assessmentCount")[0]);
            numberOfAssessmentsAdded = newAssessmentTotal - oldAssessmentTotal;
        }

        if (numberOfAssessmentsAdded > 0) {
            // get the sequence of the last assessment in the goal version
            // we'll increment this sequence as we add each new assessment
            Integer sequence = Integer.parseInt(requestMap.get("assessmentSequence")[0]);

            for (int newId = 1; newId <= numberOfAssessmentsAdded; newId++) {
                Integer formIndex = newId + oldAssessmentTotal;
                // check that newly added assignments were not removed afterwards
                parameterKey = "appraisal.assessment.deleted." + formIndex;
                String[] deletedFlag = requestMap.get(parameterKey);
                if (deletedFlag != null && deletedFlag[0].equals("0")) {
                    sequence++; // only increase sequence when we add an assessment
                    sequenceToFormIndex.put(sequence, formIndex.toString());

                    List<CriterionArea> criterionAreas = new ArrayList<CriterionArea>();
                    for (AssessmentCriteria assessmentCriteria : assessments.iterator()
                            .next().getSortedAssessmentCriteria()) {
                        criterionAreas.add(assessmentCriteria.getCriteriaArea());
                    }
                    Assessment assessment = AppraisalMgr.createNewAssessment(appraisal
                            .getUnapprovedGoalsVersion(), sequence, criterionAreas);
                    assessments.add(assessment);
                }
            }
        }
        // end adding new goals
        return sequenceToFormIndex;
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
     * @param requestMap
     */
    private void saveRecommendedIncrease(Map<String, String[]> requestMap) throws ModelException {
        // get the salary validation values. They change depending on whether current salary is
        // above or below the midpoint
        Map<String, String> salaryValidationValues = getSalaryValidationValues();
        Double increaseRate2Value = Double.parseDouble(salaryValidationValues.get("increaseRate2Value"));
        Double increaseRate1MinVal = Double.parseDouble(salaryValidationValues.get("increaseRate1MinVal"));
        Double increaseRate1MaxVal= Double.parseDouble(salaryValidationValues.get("increaseRate1MaxVal"));

        Salary salary = appraisal.getSalary();
        Double increaseValue = 0d;
        if (appraisal.getRating() == 1 && request.getParameter("save-draft") == null) {
            // can only specify an increase if the salary is not at the top pay range
            if (salary.getCurrent() < salary.getHigh()) {
                Double submittedIncrease = Double.parseDouble(requestMap.get("appraisal.salary.increase")[0]);
                if (submittedIncrease >= increaseRate1MinVal && submittedIncrease <= increaseRate1MaxVal) {
                    increaseValue = submittedIncrease;
                } else {
                    throw new ModelException(resource.getString("appraisal-salary-increase-error-invalid-change"));
                }
            }
        } else if (appraisal.getRating() == 2) {
            increaseValue = increaseRate2Value;
        }

        salary.setIncrease(increaseValue);
    }

    /**
     * Figures out the appraisal step key for the button that the user pressed when the appraisal
     * form was submitted.
     *
     * @param requestMap
     * @param appointmentType
     * @return
     */
    private AppraisalStep getAppraisalStep(Map requestMap, String appointmentType) {
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
            if (requestMap.get(button) != null) {
                appraisalStepKey = button + "-" + appointmentType;
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
        EvalsPDF PdfGenerator = new EvalsPDF(rootDir, appraisal, resource, dirName, env);
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
     * @param request
     * @param employeeResponse
     * @return
     */
    private boolean submittedRebuttal(Map<String, String[]> request, String employeeResponse) {
        return request.get("sign-appraisal") != null &&
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
            appraisals = actionHelper.getMyActiveAppraisals();
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

        boolean isReviewer = actionHelper.getReviewer() != null;
        // Permission checks
        if (!isReviewer
                || appraisal.getEmployeeSignedDate() == null
                || appraisal.getRole().equals("employee")
                || !appraisal.getStatus().equals("completed"))
        {
            return errorHandler.handleAccessDenied(request, response);
        }

        actionHelper.addToRequestMap("id", appraisal.getId());

        if (!isReviewer) {
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
        boolean isAdminOrReviewer = userRole.equals(ActionHelper.ROLE_ADMINISTRATOR )
                || userRole.equals(ActionHelper.ROLE_REVIEWER);
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

        if (!userRole.equals(ActionHelper.ROLE_ADMINISTRATOR ) &&
                !userRole.equals(ActionHelper.ROLE_REVIEWER)) {
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

        // send email to supervisor
        MailerInterface mailer = (MailerInterface) actionHelper.getPortletContextAttribute("mailer");
        EmailType emailType = appraisalStep.getEmailType();
        mailer.sendMail(appraisal, emailType);

        // update status
        appraisal.setOriginalStatus(appraisal.getStatus());
        appraisal.setStatus(appraisalStep.getNewStatus());
        AppraisalMgr.updateAppraisalStatus(appraisal);
        SessionMessages.add(request, "appraisal-goals-reactivation-requested");

        // create goalVersion pojo && associate it
        AppraisalMgr.addGoalVersion(appraisal);

        // update status of cached appraisal object
        updateAppraisalInSession();

        return display(request, response);
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
