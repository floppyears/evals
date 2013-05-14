<%
// This file renders a single assessment in the form with: goals, employee/supervisor results and
// assessment criteria checkboxes. It loops through the assessment.sortedAssessmentCriteria to
// include appraisals/assessmentCriteria which is the jsp that generates the checkboxes

Assessment formAssessment = (Assessment) pageContext.getAttribute("assessment");
%>
<fieldset class="appraisal-assessment-${assessment.id}">
<h3 class="secret"><liferay-ui:message key="appraisal-assessment-header"/>${loopStatus.index + 1}</h3>
<legend>
    <liferay-ui:message key="appraisal-assessment-header"/>${loopStatus.index + 1}
    <c:if test="${permissionRule.goals == 'e'}">
        <a class="delete img-only assessment-delete delete.id.${assessment.id}"
           title="<liferay-ui:message key="appraisal-assessment-delete"/>"
           href="#"><liferay-ui:message key="appraisal-assessment-delete"/></a>
    </c:if>
</legend>

<input type="hidden" class="appraisal-assessment-deleted-${assessment.id}" name="<portlet:namespace />appraisal.assessment.deleted.${assessment.id}"
       value="0"/>
<c:choose>
    <c:when test="${permissionRule.goals == 'e'}">
        <label for="<portlet:namespace />appraisal.goal.${assessment.id}"><liferay-ui:message key="appraisal-goals" /></label>
        <liferay-ui:input-textarea param="appraisal.goal.${assessment.id}"
            defaultValue="${assessment.goal}" />
    </c:when>
    <c:when test="${permissionRule.goals == 'v'}">
        <fieldset>
            <legend><liferay-ui:message key="appraisal-goals" /></legend>
            <p class="pass-form-text"><%= CWSUtil.escapeHtml(formAssessment.getGoal()) %></p>
        </fieldset>
    </c:when>
</c:choose>
<c:if test="${permissionRule.goals != null}">
    <c:forEach var="assessmentCriteria" items="${assessment.sortedAssessmentCriteria}" varStatus="loopStatus2">
        <%@ include file="/jsp/appraisals/assessmentCriteria.jsp"%>
    </c:forEach>
</c:if>


<c:choose>
    <c:when test="${permissionRule.results == 'e'}">
        <label for="<portlet:namespace />assessment.employeeResult.${assessment.id}"><liferay-ui:message key="appraisal-employee-results" /></label>
        <liferay-ui:input-textarea param="assessment.employeeResult.${assessment.id}"
            defaultValue="${assessment.employeeResult}" />
    </c:when>
    <c:when test="${permissionRule.results == 'v'}">
        <fieldset>
            <legend><liferay-ui:message key="appraisal-employee-results" /></legend>
            <p class="pass-form-text"><%= CWSUtil.escapeHtml(formAssessment.getEmployeeResult()) %></p>
        </fieldset>
    </c:when>
</c:choose>

<c:choose>
    <c:when test="${permissionRule.supervisorResults == 'e'}">
        <label for="<portlet:namespace />assessment.supervisorResult.${assessment.id}"><liferay-ui:message key="appraisal-result-comments" /></label>
        <liferay-ui:input-textarea param="assessment.supervisorResult.${assessment.id}"
            defaultValue="${assessment.supervisorResult}" />
    </c:when>
    <c:when test="${permissionRule.supervisorResults == 'v'}">
        <fieldset>
            <legend><liferay-ui:message key="appraisal-result-comments" /></legend>
            <p class="pass-form-text"><%= CWSUtil.escapeHtml(formAssessment.getSupervisorResult()) %></p>
        </fieldset>
    </c:when>
</c:choose>
</fieldset>