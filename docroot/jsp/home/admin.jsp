<jsp:useBean id="admin" class="edu.osu.cws.pass.models.Admin" scope="request" />
<%
PortletURL criteriaListURL = renderResponse.createRenderURL();
criteriaListURL.setWindowState(WindowState.NORMAL);
criteriaListURL.setParameter("action", "listCriteria");

PortletURL adminListURL = renderResponse.createRenderURL();
adminListURL.setWindowState(WindowState.NORMAL);
adminListURL.setParameter("action", "listAdmin");
%>

<c:if test="${admin.id ne 0}">
    <div id="<portlet:namespace/>accordionMenuPassAdmin" class="accordion-menu">
        <div class="accordion-header" onclick="<portlet:namespace/>toggleContent('<portlet:namespace/>passAdmin');">
            <table>
                <tr>
                    <td align='left'><h2 class="accordion-header-left"></h2></td>
                    <td align='left' class="accordion-header-middle">
                        <span class="accordion-header-content" id="<portlet:namespace/>_header_1">
                            &nbsp;&nbsp;<img id="<portlet:namespace/>passAdminImageToggle" src="/cps/images/accordion/accordion_arrow_down.png"/>
                        </span>
                        <span class="accordion-header-content">PASS Administration</span>
                    </td>
                    <td align='right'><h2 class="accordion-header-right"></h2></td>
                </tr>
            </table>
        </div>
        <div class="accordion-content" id="<portlet:namespace/>passAdmin" style="display: block;">
            <ul class="pass-menu-list">
                <li>
                    <a href="<%= criteriaListURL.toString() %>">Evaluation Criteria</a>
                </li>
                <li>
                    <a href="<%= adminListURL.toString() %>"><liferay-ui:message key="admins-list-title"/></a>
                </li>
            </ul>
        </div>
    </div>
</c:if>

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