package edu.osu.cws.evals.portlet;

import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.ParamUtil;
import edu.osu.cws.evals.hibernate.BusinessCenterMgr;
import edu.osu.cws.evals.hibernate.EmployeeMgr;
import edu.osu.cws.evals.hibernate.ReviewerMgr;
import edu.osu.cws.evals.models.BusinessCenter;
import edu.osu.cws.evals.models.Employee;
import edu.osu.cws.evals.models.ModelException;
import edu.osu.cws.evals.models.Reviewer;

import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import java.util.ArrayList;

public class ReviewersAction implements ActionInterface {
    private ActionHelper actionHelper = new ActionHelper();

    private HomeAction homeAction;

    /**
     * Handles listing the reviewer users. It only performs error checking. The list of
     * reviewers is already set by EvalsPortlet.portletSetup, so we don't need to do
     * anything else in this method.
     *
     * @param request
     * @param response
     * @return
     */
    public String list(PortletRequest request, PortletResponse response) throws Exception {
        // Check that the logged in user is admin
        if (!actionHelper.isLoggedInUserAdmin(request)) {
            actionHelper.addErrorsToRequest(request, ActionHelper.ACCESS_DENIED);
            return homeAction.display(request, response);
        }

        actionHelper.refreshContextCache();
        ArrayList<Reviewer> reviewersList = (ArrayList<Reviewer>) actionHelper.getPortletContextAttribute("reviewersList");
        BusinessCenterMgr businessCenterMgr = new BusinessCenterMgr();
        ArrayList<BusinessCenter> businessCenters = (ArrayList<BusinessCenter>) businessCenterMgr.list();

        actionHelper.addToRequestMap("isMaster", actionHelper.isLoggedInUserMasterAdmin(request));
        actionHelper.addToRequestMap("reviewersList", reviewersList);
        actionHelper.addToRequestMap("businessCenters", businessCenters);
        actionHelper.useMaximizedMenu(request);

        return Constants.JSP_REVIEWER_LIST;
    }

    /**
     * Handles adding an admin user.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String add(PortletRequest request, PortletResponse response) throws Exception {
        // Check that the logged in user is admin
        if (!actionHelper.isLoggedInUserAdmin(request)) {
            actionHelper.addErrorsToRequest(request, ActionHelper.ACCESS_DENIED);
            return homeAction.display(request, response);
        }

        String onid = ParamUtil.getString(request, "onid");
        String businessCenterName = ParamUtil.getString(request, "businessCenterName");

        // Check whether or not the user is already an admin user
        EmployeeMgr employeeMgr = new EmployeeMgr();
        Employee onidUser = employeeMgr.findByOnid(onid, null);
        if (actionHelper.getReviewer(onidUser.getId()) != null) {
            actionHelper.addErrorsToRequest(request, "This user is already a reviewer.");
            return list(request, response);
        }

        try {
            ReviewerMgr reviewerMgr = new ReviewerMgr();
            reviewerMgr.add(onid, businessCenterName);
            actionHelper.setEvalsReviewers(true);
            SessionMessages.add(request, "reviewer-added");
        } catch (Exception e) {
            actionHelper.addErrorsToRequest(request, e.getMessage());
        }

        return list(request, response);
    }

    /**
     * Handles deleting a reviewer user.
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public String delete(PortletRequest request, PortletResponse response) throws Exception {
        // Check that the logged in user is admin
        if (!actionHelper.isLoggedInUserAdmin(request)) {
            actionHelper.addErrorsToRequest(request, ActionHelper.ACCESS_DENIED);
            return homeAction.display(request, response);
        }

        int id = ParamUtil.getInteger(request, "id");
        ReviewerMgr reviewerMgr = new ReviewerMgr();
        try {

            // If the user clicks on the delete link the first time, use confirm page
            if (request instanceof RenderRequest && response instanceof RenderResponse) {
                Reviewer reviewer = reviewerMgr.get(id);
                if (reviewer.getEmployee() != null) {
                    reviewer.getEmployee().getName(); // initialize name due to lazy-loading
                }
                actionHelper.addToRequestMap("reviewer", reviewer);
                return Constants.JSP_REVIEWER_DELETE;
            }

            // If user hits cancel, send them to list admin page
            if (!ParamUtil.getString(request, "cancel").equals("")) {
                return list(request, response);
            }

            reviewerMgr.delete(id);
            actionHelper.setEvalsReviewers(true);
            SessionMessages.add(request, "reviewer-deleted");
        } catch (ModelException e) {
            actionHelper.addErrorsToRequest(request, e.getMessage());
        }

        return list(request, response);
    }

    public void setActionHelper(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
    }

    public void setHomeAction(HomeAction homeAction) {
        this.homeAction = homeAction;
    }
}