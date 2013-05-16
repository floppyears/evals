package edu.osu.cws.evals.portlet;

import edu.osu.cws.evals.models.Evals;

import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import java.util.ResourceBundle;

public class ErrorHandler extends Evals {

    private ActionHelper actionHelper;
    private HomeAction homeAction;

    public ErrorHandler(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
        this.homeAction = new HomeAction();
        homeAction.setActionHelper(actionHelper);
    }


    public String handleAccessDenied(PortletRequest request, PortletResponse response)
            throws Exception {
        ResourceBundle resource = (ResourceBundle) actionHelper.getPortletContextAttribute("resourceBundle");
        actionHelper.addErrorsToRequest(resource.getString("access-denied"));
        return homeAction.display(request, response);
    }

}
