<div class="osu-accordion-header" onclick="<portlet:namespace/>toggleContent('<portlet:namespace/>AppraisalInfo');">
  <img id="<portlet:namespace/>AppraisalInfoImageToggle" src="/cps/images/accordion/accordion_arrow_up.png"/>
  <liferay-ui:message key="appraisal-info" />
</div>
<div class="accordion-content" id="<portlet:namespace/>AppraisalInfo" style="display: block">
    <table class="appraisal-info collapses">
        <tbody>
            <tr>
                <th><liferay-ui:message key="employee" />:</th>
                <td><c:out value="${appraisal.job.employee.name}" /></td>
                <th><liferay-ui:message key="supervisor" />:</th>
                <td><c:out value="${appraisal.job.currentSupervisor.employee.name}" /></td>
                <th><liferay-ui:message key="job-title" />:</th>
                <td><c:out value="${appraisal.job.jobTitle}" /></td>
                <th><liferay-ui:message key="position-no" />:</th>
                <td><c:out value="${appraisal.job.positionNumber}" /></td>
                <th><liferay-ui:message key="job-start-date"  />:</th>
                <td><fmt:formatDate value="${appraisal.job.beginDate}" pattern="MM/dd/yy"/></td>
            </tr>
            <tr>
                <th><liferay-ui:message key="appraisal-employee-id"/>:</th>
                <td>${appraisal.job.employee.osuid}</td>
                <th><liferay-ui:message key="appraisal-type"/>:</th>
                <td><liferay-ui:message key="appraisal-type-${appraisal.type}"/></td>
                <th><liferay-ui:message key="reviewPeriod" />:</th>
                <td>${appraisal.reviewPeriod}</td>
                <th><liferay-ui:message key="status" />:</th>
                <td><liferay-ui:message key="${appraisal.status}" /></td>
                <th><liferay-ui:message key="appraisal-rating"/>:</th>
                <td>
                    <c:if test="${not empty appraisal.rating and (permissionRule.evaluation == 'v' or permissionRule.evaluation == 'e')}">
                        <liferay-ui:message key="appraisal-rating-pdf-${appraisal.rating}"/>
                    </c:if>
                </td>
            </tr>
        </tbody>
    </table>
</div>
