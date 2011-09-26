package edu.osu.cws.evals.tests;

import edu.osu.cws.evals.hibernate.PermissionRuleMgr;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;

@Test
public class PermissionRulesTest {
    PermissionRuleMgr permissionRuleMgr = new PermissionRuleMgr();

    /**
     * This setup method is run before this class gets executed in order to
     * set the Hibernate environment to TESTING. This will ensure that we use
     * the testing db for tests.
     *
     */
    @BeforeClass
    public void setUp() throws Exception {
        DBUnit dbunit = new DBUnit();
        dbunit.seedDatabase();
    }

    @Test(groups = {"unittest"})
    public void shouldListAllPermissionRules() throws Exception {
        HashMap rules = permissionRuleMgr.list();
        assert rules.containsKey("goalsDue-employee") : "Invalid key in permissions rules";
        assert rules.containsKey("goalsDue-immediate-supervisor") : "Invalid key in permissions rules";
        assert rules.size() == 2 :
        "PermissionRuleMgr.list() should find all permission rules";

    }
}