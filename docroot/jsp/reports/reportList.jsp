<c:if test="${!empty listAppraisals}">
  <div class="osu-cws-report-left chart-content">
    <div id="<portlet:namespace/>accordionMenuReportList" class="accordion-menu">
        <div class="osu-accordion-header">
            <liferay-ui:message key="report-types" />
        </div>
        <div class="osu-accordion-content">
          <h3><liferay-ui:message key="report-list-title"/></h3>
          <table class="taglib-search-iterator report-data">
              <thead>
                  <tr class="portlet-section-header results-header">
                      <th><liferay-ui:message key="employee"/></th>
                      <th><liferay-ui:message key="reviewPeriod"/></th>
                      <th><liferay-ui:message key="overdue"/></th>
                      <th><liferay-ui:message key="status"/></th>
                  </tr>
              </thead>
              <tbody>
              <c:forEach var="appraisal" items="${listAppraisals}" varStatus="loopStatus">
                  <tr class="${loopStatus.index % 2 == 0 ? 'portlet-section-body results-row' :
                          'portlet-section-alternate results-row alt'}">
                      <td><c:out value="${appraisal.job.employee.name}"/></td>
                      <td><c:out value="${appraisal.reviewPeriod}"/></td>
                      <td><c:out value="${appraisal.viewOverdue}"/></td>
                      <td><a href="<portlet:actionURL windowState="<%= WindowState.MAXIMIZED.toString()%>">
                                      <portlet:param name="id" value="${appraisal.id}"/>
                                      <portlet:param  name="action" value="display"/>
                                      <portlet:param  name="controller" value="AppraisalsAction"/>
                                     </portlet:actionURL>">
                          <liferay-ui:message key="${appraisal.viewStatus}"/></a></td>
                  </tr>
              </c:forEach>
              </tbody>
          </table>
        </div>
      </div>
  </div>
  <div class="osu-cws-clear-both"></div>
</c:if>