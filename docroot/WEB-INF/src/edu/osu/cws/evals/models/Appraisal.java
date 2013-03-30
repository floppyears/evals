package edu.osu.cws.evals.models;

import java.text.MessageFormat;
import java.util.*;

public class Appraisal extends Evals {

    public static final String TYPE_ANNUAL = "annual";

    public static final String TYPE_INITIAL = "initial";

    public static final String TYPE_TRIAL = "trial";

    public static final String STATUS_APPRAISAL_DUE = "appraisalDue";
    public static final String STATUS_APPRAISAL_OVERDUE = "appraisalOverdue";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_BACK_ORIG_STATUS = "backToOriginalStatus";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_GOALS_APPROVAL_DUE = "goalsApprovalDue";
    public static final String STATUS_GOALS_APPROVAL_OVERDUE = "goalsApprovalOverdue";
    public static final String STATUS_GOALS_APPROVED = "goalsApproved";
    public static final String STATUS_GOALS_DUE = "goalsDue";
    public static final String STATUS_GOALS_OVERDUE = "goalsOverdue";
    public static final String STATUS_GOALS_REACTIVATED = "goalsReactivated";
    public static final String STATUS_GOALS_REQUIRED_MODIFICATION = "goalsRequiredModification";
    public static final String STATUS_REBUTTAL_READ_DUE = "rebuttalReadDue";
    public static final String STATUS_REBUTTAL_READ_OVERDUE = "rebuttalReadOverdue";
    public static final String STATUS_RELEASE_DUE = "releaseDue";
    public static final String STATUS_RELEASE_OVERDUE = "releaseOverdue";
    public static final String STATUS_RESULTS_DUE = "resultsDue";
    public static final String STATUS_RESULTS_OVERDUE = "resultsOverdue";
    public static final String STATUS_REVIEW_DUE = "reviewDue";
    public static final String STATUS_REVIEW_OVERDUE = "reviewOverdue";
    public static final String STATUS_SIGNATURE_DUE = "signatureDue";
    public static final String STATUS_SIGNATURE_OVERDUE = "signatureOverdue";
    public static final String STATUS_IN_REVIEW = "inReview";

    public static final String STAGE_GOALS = "goals";
    public static final String STAGE_RESULTS = "results";
    public static final String STAGE_APPRAISAL = "appraisal";
    public static final String STAGE_EVALUATION = "evaluation";
    public static final String STAGE_RELEASE = "release";
    public static final String STAGE_SIGNATURE = "signature";
    public static final String STAGE_COMPLETED = "completed";
    public static final String STAGE_REBUTTAL = "rebuttal";
    public static final String STAGE_CLOSED = "closed";
    public static final String STAGE_ARCHIVED = "archived";

    public static final String DUE = "Due";
    public static final String OVERDUE = "Overdue";

    private int id;

    /**
     * Appraisals are related to a job instead of the employee because an
     * employee may hold multiple jobs.
     */
    private Job job;

    private String status;

    /**
     * The beginning of the date period for the appraisal.
     */
    private Date startDate;

    private Date goalsSubmitDate;

    /**
     * The end of the date period for the appraisal.
     */
    private Date endDate;

    /**
     * The employee's supervisor that approves the employee's goals
     */
    private Employee evaluator;

    private Date evaluationSubmitDate;

    private Date goalApprovedDate;

    private Employee goalsApprover;

    /**
     * Comments entered by the supervisor regarding the employee's goals
     */
    private String goalsComments;

    private Date goalsRequiredModificationDate;

    public void setGoalsRequiredModificationDate(Date goalsRequiredModificationDate) {
        this.goalsRequiredModificationDate = goalsRequiredModificationDate;
    }

    public Date getGoalsRequiredModificationDate() {
        return goalsRequiredModificationDate;
    }

    private Date resultSubmitDate;

    /**
     * Appraisal evaluation filled out by the supervisor and reviewed
     * by business center reviewer.
     */
    private String evaluation;

    /**
     * Rating given to the employee during the evaluation step
     */
    private Integer rating;

    /**
     * Business center employee approving the supervisor's evaluation
     */
    private Employee reviewer;

    private Date reviewSubmitDate;

    private String reviewStatusID;

    /**
     * Comments/feedback that business center provides to supervisor's
     * evaluation.
     */
    private String review;

    private Date createDate;

    private String rebuttal;

    private Date rebuttalDate;

    private Date employeeSignedDate;

    private Date releaseDate;

    private Date supervisorRebuttalRead;

    private String type;

    private Date closeOutDate;

    private Employee closeOutBy;

    private CloseOutReason closeOutReason;

    private Employee reopenedBy;

    private Date reopenedDate;

    private String reopenReason;

    private String originalStatus;

    private Integer overdue;

    private Set<Assessment> assessments = new HashSet<Assessment>();

    private Integer goalsOverdue;

    private Integer goalsApprovalOverdue;

    private Integer resultsOverdue;

    private Integer appraisalOverdue;

    private Integer reviewOverdue;

    private Integer releaseOverdue;

    private Integer signatureOverdue;

    private Integer rebuttalReadOverdue;

    /**
     * Read only propety not stored in the db. It is the role of the logged in user with
     * regards to this appraisal.
     */
    private String role;

    private ArrayList<String> statusHiddenFromEmployee = new ArrayList<String>();

    public Appraisal() { }

    /**
     * Constructor used by AppraisalMgr to fetch only a limited set of attributes. Used to
     * display information in my status section and supervisor report (evaluations of current
     * supervisor).
     *
     * @param id
     * @param jobTitle
     * @param startDate
     * @param endDate
     * @param status
     */
    public Appraisal(int id, String jobTitle, Date startDate, Date endDate, String status,
                     Integer overdue) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.job = new Job();
        this.job.setJobTitle(jobTitle);

        if (overdue == null) {
            this.overdue = -999;
        } else {
            this.overdue = overdue;
        }
    }

    /**
     * Constructor used by AppraisalMgr to fetch only a limited set of attributes. Used to display
     * information in my team section.
     *
     * @param id
     * @param jobTitle
     * @param jobTitle
     * @param lastName
     * @param firstName
     * @param appointmentType
     * @param startDate
     * @param endDate
     * @param status
     * @param goalsRequiredModificationDate
     * @param employeeSignedDate
     * @param employeeId
     * @param overdue
     */
    public Appraisal(int id, String jobTitle, String lastName, String firstName, String appointmentType,
                     Date startDate, Date endDate, String status, Date goalsRequiredModificationDate,
                     Date employeeSignedDate, int employeeId, Integer overdue) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setLastName(lastName);
        employee.setFirstName(firstName);
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.job = new Job();
        this.job.setJobTitle(jobTitle);
        this.job.setAppointmentType(appointmentType);
        this.job.setEmployee(employee);
        this.employeeSignedDate = employeeSignedDate;
        this.goalsRequiredModificationDate = goalsRequiredModificationDate;

        if (overdue == null) {
            this.overdue = -999;
        } else {
            this.overdue = overdue;
        }
    }

    /**
     * Constructor used by the getReviews and search method in appraisal mgr to fetch a list
     * of appraisal objects
     *
     * @param id
     * @param jobTitle
     * @param positionNumber
     * @param startDate
     * @param endDate
     * @param type
     * @param employeeId
     * @param lastName
     * @param firstName
     * @param evaluationSubmitDate
     * @param status
     * @param orgCodeDescription
     */
    public Appraisal(int id, String jobTitle, String positionNumber, Date startDate, Date endDate,
                     String type, int employeeId, String lastName, String firstName, Date evaluationSubmitDate,
                     String status, String bcName, String orgCodeDescription, String suffix,
                     Integer overdue) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setLastName(lastName);
        employee.setFirstName(firstName);

        Job tempJob = new Job();
        tempJob.setJobTitle(jobTitle);
        tempJob.setOrgCodeDescription(orgCodeDescription);
        tempJob.setEmployee(employee);
        tempJob.setPositionNumber(positionNumber);
        tempJob.setBusinessCenterName(bcName);
        tempJob.setSuffix(suffix);

        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.type = type;
        this.evaluationSubmitDate = evaluationSubmitDate;
        this.status = status;
        this.job = tempJob;

        if (overdue == null) {
            this.overdue = -999;
        } else {
            this.overdue = overdue;
        }
    }

    /**
     * Constructor used by ReportMgr.getReportListHQL. It only fetches the data that it needs. The
     * employee.id and job's pidm, posno and suffix are dummy data since they are only needed
     * to construct the object.
     *
     * @param id
     * @param firstName
     * @param lastName
     * @param startDate
     * @param endDate
     * @param status
     * @param overdue
     * @param employeeId
     * @param positionNumber
     * @param suffix
     */
    public Appraisal(int id, String firstName, String lastName, Date startDate, Date endDate,
                     String status, Integer overdue, int employeeId, String positionNumber, String suffix) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setLastName(lastName);
        employee.setFirstName(firstName);

        Job tempJob = new Job();
        tempJob.setPositionNumber(positionNumber);
        tempJob.setSuffix(suffix);
        tempJob.setEmployee(employee);

        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.job = tempJob;

        if (overdue == null) {
            this.overdue = -999;
        } else {
            this.overdue = overdue;
        }
    }

    /**
     * Used to copy an appraisal object. This is used by appraisal search or report list appraisal.
     * This is done so that the jsp files don't complain about missing employee or job records in
     * the db.
     *
     * @param appraisal
     */
    public Appraisal(Appraisal appraisal) {
        this.id = appraisal.getId();
        if (appraisal.getStartDate() != null) {
            setStartDate(appraisal.getStartDate());
        }
        if (appraisal.getEndDate() != null) {
            setEndDate(appraisal.getEndDate());
        }
        if (appraisal.getType() != null) {
            setType(appraisal.getType());
        }
        if (appraisal.getEvaluationSubmitDate() != null) {
            setEvaluationSubmitDate(appraisal.getEvaluationSubmitDate());
        }
        if (appraisal.getStatus() != null) {
            setStatus(appraisal.getStatus());
        }
        if (appraisal.getOverdue() != null) {
            setOverdue(appraisal.getOverdue());
        }
        if (appraisal.getStatus() != null) {
            setStatus(appraisal.getStatus());
        }
        if (appraisal.getStatus() != null) {
            setStatus(appraisal.getStatus());
        }
        setJob(appraisal.getJob());
    }

    /**
     * Uses the start date and end date to generate the review period.
     *
     * @return
     */
    public String getReviewPeriod() {
        if (startDate == null) {
            startDate = new Date();
        }
        if (endDate == null) {
            endDate = new Date();
        }

        return MessageFormat.format("{0,date,MM/dd/yy} - {1,date,MM/dd/yy}",
                new Object[]{getStartDate(), getEndDate()});
    }

    /**
     * Returns a sorted list of assessments. The assessment pojo class
     * implements comparable interface which makes this easy.
     *
     * @return
     */
    public List getSortedAssessments() {
        List sortedAssessments = new ArrayList(assessments);
        Collections.sort(sortedAssessments);
        return sortedAssessments;
    }

    /**
     * Compares various date fields of the appraisal object to figure out when was the last modified
     * date of the appraisal. The fields that are compared are: evaluationSubmitDate, goalsSubmitDate,
     * goalApprovedDate, resultSubmitDate, reviewSubmitDate, rebuttalDate, employeeSignedDate,
     * releaseDate, supervisorRebuttalRead, closeOutDate, reopenedDate
     *
     * @return lastModified
     */
    public Date getLastModified() {
        Date lastModified = createDate;
        Date fieldsToCompare[] = {evaluationSubmitDate, goalsSubmitDate, goalApprovedDate,
                resultSubmitDate, reviewSubmitDate, rebuttalDate, employeeSignedDate,
                releaseDate, supervisorRebuttalRead, closeOutDate, reopenedDate};

        for (Date appraisalDate : fieldsToCompare) {
            if (appraisalDate != null && appraisalDate.after(lastModified)) {
                lastModified = appraisalDate;
            }
        }

        return lastModified;
    }

    /**
     *  Checks the appraisal status and if we need to change the status based on the user role, the status
     * is changed. Right now, if the supervisor submitted the appraisal or hr submitted comments, the status
     * displayed to the user is in review. If the status contains rebuttalRead, we set the status to
     * completed.
     *
     * @return status
     */
    public String getViewStatus() {
        String viewStatus = status;

        statusHiddenFromEmployee.add(STATUS_APPRAISAL_DUE);
        statusHiddenFromEmployee.add(STATUS_APPRAISAL_OVERDUE);
        statusHiddenFromEmployee.add(STATUS_REVIEW_DUE);
        statusHiddenFromEmployee.add(STATUS_REVIEW_OVERDUE);
        statusHiddenFromEmployee.add(STATUS_RELEASE_DUE);
        statusHiddenFromEmployee.add(STATUS_RELEASE_OVERDUE);

        if (getRole().equals("employee") &&  statusHiddenFromEmployee.contains(viewStatus)) {
            viewStatus = STATUS_IN_REVIEW;
        }

        // Whenever the status is rebuttalReadDue or rebuttalReadOverdue, we set it as completed.
        if (viewStatus.contains("rebuttalRead")) {
            viewStatus = STATUS_COMPLETED;
        }

        return viewStatus;
    }

    public boolean isOpen() {
        String viewStatus = getViewStatus();
        return !status.equals(STATUS_CLOSED) && !viewStatus.equals(STATUS_COMPLETED)
                && !status.equals(STATUS_ARCHIVED);
    }


    /**
     * Given a status, it returns the respective stage.
     *
     * @param status
     * @return
     */
    public static String getStage(String status) {
        String stage;
        if (status.equals(Appraisal.STATUS_GOALS_APPROVED) ) {
            stage = Appraisal.STAGE_RESULTS;
        } else if (status.contains("goals")) {
            stage = Appraisal.STAGE_GOALS;
        } else {
            stage = status.replace("Due", "").replace("Overdue", "");
        }

        if (stage.equals("rebuttalRead")) {
            stage = Appraisal.STAGE_REBUTTAL;
        }

        return stage;
    }

    /**
     * Used by the report list data. If the object is not overdue, we display - .
     *
     * @return
     */
    public String getViewOverdue() {
        if (overdue < 1) {
            return "-";
        }
        return overdue.toString();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Date getGoalsSubmitDate() {
        return goalsSubmitDate;
    }

    public void setGoalsSubmitDate(Date goalsSubmitDate) {
        this.goalsSubmitDate = goalsSubmitDate;
    }

    public Employee getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(Employee evaluator) {
        this.evaluator = evaluator;
    }

    public Date getEvaluationSubmitDate() {
        return evaluationSubmitDate;
    }

    public void setEvaluationSubmitDate(Date evaluationSubmitDate) {
        this.evaluationSubmitDate = evaluationSubmitDate;
    }

    public Date getGoalApprovedDate() {
        return goalApprovedDate;
    }

    public void setGoalApprovedDate(Date goalApprovedDate) {
        this.goalApprovedDate = goalApprovedDate;
    }

    public Employee getGoalsApprover() {
        return goalsApprover;
    }

    public void setGoalsApprover(Employee goalsApprover) {
        this.goalsApprover = goalsApprover;
    }

    public String getGoalsComments() {
        return goalsComments;
    }

    public void setGoalsComments(String goalsComments) {
        this.goalsComments = goalsComments;
    }

    public Date getResultSubmitDate() {
        return resultSubmitDate;
    }

    public void setResultSubmitDate(Date resultSubmitDate) {
        this.resultSubmitDate = resultSubmitDate;
    }

    public String getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(String evaluation) {
        this.evaluation = evaluation;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Employee getReviewer() {
        return reviewer;
    }

    public void setReviewer(Employee reviewer) {
        this.reviewer = reviewer;
    }

    public Date getReviewSubmitDate() {
        return reviewSubmitDate;
    }

    public void setReviewSubmitDate(Date reviewSubmitDate) {
        this.reviewSubmitDate = reviewSubmitDate;
    }

    public String getReviewStatusID() {
        return reviewStatusID;
    }

    public void setReviewStatusID(String reviewStatusID) {
        this.reviewStatusID = reviewStatusID;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public String getRebuttal() {
        return rebuttal;
    }

    public void setRebuttal(String rebuttal) {
        this.rebuttal = rebuttal;
    }

    public Date getRebuttalDate() {
        return rebuttalDate;
    }

    public void setRebuttalDate(Date rebuttalDate) {
        this.rebuttalDate = rebuttalDate;
    }

    public Date getEmployeeSignedDate() {
        return employeeSignedDate;
    }

    public void setEmployeeSignedDate(Date employeeSignedDate) {
        this.employeeSignedDate = employeeSignedDate;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Date getSupervisorRebuttalRead() {
        return supervisorRebuttalRead;
    }

    public void setSupervisorRebuttalRead(Date supervisorRebuttalRead) {
        this.supervisorRebuttalRead = supervisorRebuttalRead;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Date getCloseOutDate() {
        return closeOutDate;
    }

    public void setCloseOutDate(Date closeOutDate) {
        this.closeOutDate = closeOutDate;
    }

    public Employee getCloseOutBy() {
        return closeOutBy;
    }

    public void setCloseOutBy(Employee closeOutBy) {
        this.closeOutBy = closeOutBy;
    }

    public CloseOutReason getCloseOutReason() {
        return closeOutReason;
    }

    public void setCloseOutReason(CloseOutReason closeOutReason) {
        this.closeOutReason = closeOutReason;
    }

    public Set<Assessment> getAssessments() {
        return assessments;
    }

    public void setAssessments(Set<Assessment> assessments) {
        this.assessments = assessments;
    }

    public void addAssessment(Assessment assessment) {
        assessment.setAppraisal(this);
        this.assessments.add(assessment);
    }

    public Employee getReopenedBy() {
        return reopenedBy;
    }

    public void setReopenedBy(Employee reopenedBy) {
        this.reopenedBy = reopenedBy;
    }

    public Date getReopenedDate() {
        return reopenedDate;
    }

    public void setReopenedDate(Date reopenedDate) {
        this.reopenedDate = reopenedDate;
    }

    public String getReopenReason() {
        return reopenReason;
    }

    public void setReopenReason(String reopenReason) {
        this.reopenReason = reopenReason;
    }

    public String getOriginalStatus() {
        return originalStatus;
    }

    public void setOriginalStatus(String originalStatus) {
        this.originalStatus = originalStatus;
    }

    public Integer getOverdue() {
        return overdue;
    }

    public void setOverdue(Integer overdue) {
        this.overdue = overdue;
    }

    public Integer getGoalsOverdue() {
        return goalsOverdue;
    }

    public void setGoalsOverdue(Integer goalsOverdue) {
        this.goalsOverdue = goalsOverdue;
    }

    public Integer getGoalsApprovalOverdue() {
        return goalsApprovalOverdue;
    }

    public void setGoalsApprovalOverdue(Integer goalsApprovalOverdue) {
        this.goalsApprovalOverdue = goalsApprovalOverdue;
    }

    public Integer getResultsOverdue() {
        return resultsOverdue;
    }

    public void setResultsOverdue(Integer resultsOverdue) {
        this.resultsOverdue = resultsOverdue;
    }

    public Integer getAppraisalOverdue() {
        return appraisalOverdue;
    }

    public void setAppraisalOverdue(Integer appraisalOverdue) {
        this.appraisalOverdue = appraisalOverdue;
    }

    public Integer getReviewOverdue() {
        return reviewOverdue;
    }

    public void setReviewOverdue(Integer reviewOverdue) {
        this.reviewOverdue = reviewOverdue;
    }

    public Integer getReleaseOverdue() {
        return releaseOverdue;
    }

    public void setReleaseOverdue(Integer releaseOverdue) {
        this.releaseOverdue = releaseOverdue;
    }

    public Integer getSignatureOverdue() {
        return signatureOverdue;
    }

    public void setSignatureOverdue(Integer signatureOverdue) {
        this.signatureOverdue = signatureOverdue;
    }

    public Integer getRebuttalReadOverdue() {
        return rebuttalReadOverdue;
    }

    public void setRebuttalReadOverdue(Integer rebuttalReadOverdue) {
        this.rebuttalReadOverdue = rebuttalReadOverdue;
    }

    /**
     * Role of the logged in user for this appraisal. This is used
     * in the getViewStatus() method.
     *
     * @return
     */
    public String getRole() {
        if (role == null) {
            role = "";
        }
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
