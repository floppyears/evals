package edu.osu.cws.evals.models;

public class PermissionRule extends Evals implements Cloneable {
    private int id;

    private String status;

    private String role;

    private String approvedGoals;

    private String unapprovedGoals;

    private String goalComments;

    private String results;

    private String supervisorResults;

    private String evaluation;

    private String review;

    private String employeeResponse;

    private String rebuttalRead;

    private String saveDraft;

    private String secondarySubmit;

    private String submit;

    private String actionRequired;

    private String downloadPDF;

    private String closeOut;

    private String sendToNolij;

    private String setStatusToResultsDue;

    private String reactivateGoals;

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public PermissionRule() { }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getApprovedGoals() {
        return approvedGoals;
    }

    public void setApprovedGoals(String approvedGoals) {
        this.approvedGoals = approvedGoals;
    }

    public String getUnapprovedGoals() {
        return unapprovedGoals;
    }

    public void setUnapprovedGoals(String unapprovedGoals) {
        this.unapprovedGoals = unapprovedGoals;
    }

    public String getGoalComments() {
        return goalComments;
    }

    public void setGoalComments(String goalComments) {
        this.goalComments = goalComments;
    }

    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public String getSupervisorResults() {
        return supervisorResults;
    }

    public void setSupervisorResults(String supervisorResults) {
        this.supervisorResults = supervisorResults;
    }

    public String getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(String evaluation) {
        this.evaluation = evaluation;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public String getSaveDraft() {
        return saveDraft;
    }

    public void setSaveDraft(String saveDraft) {
        this.saveDraft = saveDraft;
    }

    public String getEmployeeResponse() {
        return employeeResponse;
    }

    public String getRebuttalRead() {
        return rebuttalRead;
    }

    public void setRebuttalRead(String rebuttalRead) {
        this.rebuttalRead = rebuttalRead;
    }

    public void setEmployeeResponse(String employeeResponse) {
        this.employeeResponse = employeeResponse;
    }

    public String getSecondarySubmit() {
        return secondarySubmit;
    }

    public void setSecondarySubmit(String secondarySubmit) {
        this.secondarySubmit = secondarySubmit;
    }

    public String getSubmit() {
        return submit;
    }

    public void setSubmit(String submit) {
        this.submit = submit;
    }

    public String getActionRequired() {
        return actionRequired;
    }

    public void setActionRequired(String actionRequired) {
        this.actionRequired = actionRequired;
    }

    public String getDownloadPDF() {
        return downloadPDF;
    }

    public void setDownloadPDF(String downloadPDF) {
        this.downloadPDF = downloadPDF;
    }

    public String getCloseOut() {
        return closeOut;
    }

    public void setCloseOut(String closeOut) {
        this.closeOut = closeOut;
    }

    public String getSendToNolij() {
        return sendToNolij;
    }

    public void setSendToNolij(String sendToNolij) {
        this.sendToNolij = sendToNolij;
    }

    public String getSetStatusToResultsDue() {
        return setStatusToResultsDue;
    }

    public void setSetStatusToResultsDue(String setStatusToResultsDue) {
        this.setStatusToResultsDue = setStatusToResultsDue;
    }

    public String getReactivateGoals() {
        return reactivateGoals;
    }

    public void setReactivateGoals(String reactivateGoals) {
        this.reactivateGoals = reactivateGoals;
    }
}
