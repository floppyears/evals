<%@ include file="/jsp/init.jsp" %>

<h2><liferay-ui:message key="${pageTitle}" /></h2>
<ul id="search-parent" class="actions">
    <li id="<portlet:namespace/>appraisal-search-link">
        <liferay-ui:icon
            image="search"
            url="#"
            label="true"
            message="Search"
        />
    </li>
</ul>

<form action="<portlet:actionURL windowState="<%= WindowState.MAXIMIZED.toString() %>">
    <portlet:param name="action" value="searchAppraisals"/>
    </portlet:actionURL>" id="<portlet:namespace />fm" name="<portlet:namespace />fm" class="search" method="post">
    <fieldset id="pass-user-add">
        <legend><liferay-ui:message key="search"/></legend>
        <label for="<portlet:namespace/>osuid"><liferay-ui:message key="osuid"/></label>
        <input type="text" id="<portlet:namespace/>osuid" class="narrow" name="<portlet:namespace/>osuid" />
        <input type="submit" value="<liferay-ui:message key="search" />" />
        <input type="submit" class="cancel" value="<liferay-ui:message key="cancel" />" id="<portlet:namespace/>cancel" />
    </fieldset>    
</form>

<c:if test="${!empty appraisals}">
    <table class="taglib-search-iterator">
        <thead>
            <tr class="portlet-section-header results-header">
                <th><liferay-ui:message key="reviewPeriod"/></th>
                <th><liferay-ui:message key="type"/></th>
                <th><liferay-ui:message key="employee"/></th>
                <th><liferay-ui:message key="job-title"/></th>
                <th><liferay-ui:message key="position-no"/></th>
                <th><liferay-ui:message key="orgn-code-desc"/></th>
                <th><liferay-ui:message key="status"/></th>
            </tr>
        </thead>
        <tbody>
    <c:forEach var="appraisal" items="${appraisals}" varStatus="loopStatus">
        <tr class="${loopStatus.index % 2 == 0 ? 'portlet-section-body results-row' : 'portlet-section-alternate results-row alt'}">
            <td>${appraisal.reviewPeriod}</td>
            <td><liferay-ui:message key="appraisal-type-${appraisal.type}"/></td>
            <td>${appraisal.job.employee.name}</td>
            <td>${appraisal.job.jobTitle}</td>
            <td>${appraisal.job.positionNumber}</td>
            <td>${appraisal.job.orgCodeDescription}</td>
            <td><a href="<portlet:actionURL windowState="<%= WindowState.MAXIMIZED.toString()%>">
                                <portlet:param name="id" value="${appraisal.id}"/>
                                <portlet:param  name="action" value="displayAppraisal"/>
                               </portlet:actionURL>">
               <liferay-ui:message key="${appraisal.viewStatus}"/></a></td>
        </tr>
    </c:forEach>
        </tbody>
    </table>
</c:if>
<c:if test="${empty appraisals}">
<p><liferay-ui:message key="no-pending-reviews"/></p>
</c:if>

<script type="text/javascript">
jQuery(document).ready(function() {
  jQuery("#pass-user-add").hide();

  // When user clicks cancel in add form, hide the form.
  jQuery("#<portlet:namespace/>cancel").click(function() {
    jQuery("#pass-user-add").hide("slow");
    jQuery("#search-parent").show("slow");
    return false;
  });
  // When user clicks cancel in add form, hide the form.
  jQuery("#<portlet:namespace/>appraisal-search-link").click(function() {
    jQuery("#pass-user-add").show("slow");
    jQuery("#search-parent").hide("slow");
  });

  // Validate form submission
  jQuery("#<portlet:namespace />fm").submit(function() {
    var errors = "";
    if (jQuery("#<portlet:namespace />osuid").val() == "") {
      errors = "<li>Please enter the employee's OSU ID</li>";
    }
    if (errors != "") {
      jQuery("#<portlet:namespace />flash").html(
        '<span class="portlet-msg-error"><ul>'+errors+'</ul></span>'
      );
      return false;
    }

    return true;
  });
});
</script>
<%@ include file="/jsp/footer.jsp" %>
