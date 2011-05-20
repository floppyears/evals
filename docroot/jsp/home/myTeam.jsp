<jsp:useBean id="myTeamsActiveAppraisals" class="java.util.ArrayList" scope="request" />

<%
myAppraisalURL = renderResponse.createRenderURL();
myAppraisalURL.setWindowState(WindowState.MAXIMIZED);
myAppraisalURL.setParameter("action", "displayAppraisal");
%>

<div id="<portlet:namespace/>accordionMenuMyTeam" class="accordion-menu">
    <div class="accordion-header" onclick="<portlet:namespace/>toggleContent('<portlet:namespace/>MyTeam');">
        <table>
            <tr>
                <td align='left'><h2 class="accordion-header-left"></h2></td>
                <td align='left' class="accordion-header-middle">
                    <span class="accordion-header-content" id="<portlet:namespace/>_header_1">
                        &nbsp;&nbsp;<img id="<portlet:namespace/>MyTeamImageToggle" src="/cps/images/accordion/accordion_arrow_down.png"/>
                    </span>
                    <span class="accordion-header-content"><liferay-ui:message key="myTeam" /></span>
                </td>
                <td align='right'><h2 class="accordion-header-right"></h2></td>
            </tr>
        </table>
    </div>
    <div class="accordion-content" id="<portlet:namespace/>MyTeam" style="display: block;">
        <c:if test="${!empty myTeamsActiveAppraisals}">
            <table class="taglib-search-iterator">
                <tr class="portlet-section-header results-header">
                    <th><liferay-ui:message key="name" /></th>
                    <th><liferay-ui:message key="appointmentType" /></th>
                    <th><liferay-ui:message key="reviewPeriod" /></th>
                    <th><liferay-ui:message key="status" /></th>
                </tr>
                <c:forEach var="shortAppraisal" items="${myTeamsActiveAppraisals}" varStatus="loopStatus">
                <%
                    appraisalMap = (java.util.HashMap) pageContext.getAttribute("shortAppraisal");
                    myAppraisalURL.setParameter("id", appraisalMap.get("id").toString());
                %>

                    <tr class="${loopStatus.index % 2 == 0 ? 'portlet-section-body results-row' : 'portlet-section-alternate results-row alt'}"
                        onmouseover="this.className = 'portlet-section-body-hover results-row hover';"
                        onmouseout="this.className = '${loopStatus.index % 2 == 0 ? 'portlet-section-body results-row' : 'portlet-section-alternate results-row alt'}';"
                    >
                        <td>${shortAppraisal.employeeName}</td>
                        <td><liferay-ui:message key="${shortAppraisal.appointmentTypeName}" /></td>
                        <td><fmt:formatDate value="${shortAppraisal.startDate}" pattern="yyyy"/> -
                            <fmt:formatDate value="${shortAppraisal.endDate}" pattern="yyyy"/>
                        </td>
                        <td><a href="<%= myAppraisalURL.toString() %>">
                        <liferay-ui:message key="${shortAppraisal.status}" /></a>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </c:if>
        <c:if test="${empty myTeamsActiveAppraisals}">
            <p><em><liferay-ui:message key="noTeamActiveAppraisals" /></em></p>
        </c:if>
    </div>
</div>

<script type="text/javascript">
    function <portlet:namespace/>toggleContent(id){
            var imgId=id+'ImageToggle';

             if(document.getElementById(id).style.display=='block'){

                var path1 = new String('/cps/images/accordion/accordion_arrow_up.png');
                document.getElementById(imgId).src = path1;
                jQuery('#' + id).hide('slow');
            }else if(document.getElementById(id).style.display=='none'){
                var path2 = new String('/cps/images/accordion/accordion_arrow_down.png');
                document.getElementById(imgId).src = path2;
                jQuery('#' + id).show('slow');
            }
        }

</script>