/**
 * This class is a hibernate class that is called by the Actions class methods and
 * receives POJOs. It performs business logic with POJOs and uses Hibernate to save
 * them back to database if appropriate.
 */
package edu.osu.cws.pass.util;

import edu.osu.cws.pass.models.*;
import org.hibernate.*;

import java.util.Iterator;
import java.util.List;

public class Criteria {

    private Employees employees = new Employees();

    /**
     * The default appointment type to use when displaying criteria information.
     */
    public static final String DEFAULT_APPOINTMENT_TYPE = AppointmentType.CLASSIFIED;

    /**
     * Takes the CriterionArea and CriterionDetail POJO objects, performs validation
     * and calls the respective hibernate method to save to db if passed validation.
     *
     * @param area      CriterionArea POJO
     * @param details   CriterionDetail POJO
     * @param creator      Username of logged in user
     * @return errors   An array of error messages, but empty array on success.
     * @throws edu.osu.cws.pass.models.ModelException
     */
    public boolean add(CriterionArea area, CriterionDetail details, Employee creator)
            throws ModelException, Exception {
        int sequence = getNextSequence(area.getAppointmentType());

        area.setCreator(creator);
        area.setSequence(sequence);
        details.setCreator(creator);

        // validate both objects individually and then check for errors
        area.validate();
        details.validate();

        if (area.getErrors().size() > 0 || details.getErrors().size() > 0) {
            return false;
        }

        Session session = HibernateUtil.getCurrentSession();
        try {
            Transaction tx = session.beginTransaction();
            session.save(area);
            area.addDetails(details);
            session.save(details);
            tx.commit();
        } catch (Exception e){
            session.close();
            throw e;
        }
        return true;

    }

    /**
     * Takes the CriterionArea and CriterionDetail POJO objects, performs validation
     * and calls the respective hibernate method to save to db if passed validation. If
     * the change is minor, either the CriterionArea or CriterionDetail POJOs are edited.
     * Otherwise if the description was changed a new CriterionDetail POJO is created, or
     * if the name was changed a new CriterionArea and CriterionDetail POJOs are created.
     *
     * @param area      CriterionArea POJO
     * @param details   CriterionDetail POJO
     * @return errors   An array of error messages, but empty array on success.
     * @throws edu.osu.cws.pass.models.ModelException
     */
    public String[] edit(CriterionArea area, CriterionDetail details)
            throws ModelException {
        return new String[2];
    }

    /**
     * Takes an appointmentType, gets a Hibernate session object and calls a private method
     * to call the private method that just uses Hibernate to fetch the list of criteria.
     *
     * @param appointmentType
     * @return criteria        List of CriterionAreas
     * @throws edu.osu.cws.pass.models.ModelException
     */
    public List<CriterionArea> list(String appointmentType) throws ModelException, Exception {
        List<CriterionArea> criteriaList;
        Session session = HibernateUtil.getCurrentSession();
        try {
            criteriaList = this.list(appointmentType, session);
        } catch (Exception e){
            session.close();
            throw e;
        }
        return criteriaList;
    }

    /**
     * Takes an appointmentType, a Hibernate session and uses Hibernate to fetch the list of
     * CriterionArea.
     *
     * @param appointmentType
     * @param session
     * @return criteria     List of CriterionAreas
     * @throws org.hibernate.HibernateException
     */
    private List<CriterionArea> list(String appointmentType, Session session) throws HibernateException {
        Transaction tx = session.beginTransaction();
        List result = session.createQuery("from edu.osu.cws.pass.models.CriterionArea where " +
                "appointmentType = :appointmentType").setString("appointmentType", appointmentType)
                .list();
        tx.commit();
        return result;

    }

    /**
     * Takes an ID of the CriterionArea the user is trying to delete. If successful, an empty
     * array is returned, otherwise, the array will contain error messages.
     *
     * @param ID
     * @return errors   An array of errors if there was a problem trying to delete the CriterionArea
     */
    public String[] delete(long ID) {
        return new String[2];
    }

    /**
     * Takes a string specifying the new order the criterias. Fetches all the CriterionArea for the
     * given employee type. Then it sets the new sequence in each one of them and saves the POJOs.
     *
     * @param order     The new order of criterias
     * @return errors
     */
    public String[] updateSequence(String order, long employeeTypeID) {
        return new String[2];
    }

    /**
     * Figures out the next available sequence for a CriterionArea of a specific appointment type.
     * The next available sequence is usually is size of criteria list + 1.
     * @param appointmentType
     * @return
     */
    public int getNextSequence(String appointmentType) throws Exception {
        int availableSequence = 0;
        Session hsession = HibernateUtil.getCurrentSession();
        try {
            Transaction tx = hsession.beginTransaction();
            Query countQry = hsession.createQuery("select count(*) from edu.osu.cws.pass.models.CriterionArea " +
                    "where appointmentType = :appointmentType");

            countQry.setString("appointmentType", appointmentType);
            countQry.setMaxResults(1);
            Iterator results = countQry.list().iterator();

            if (results.hasNext()) {
                availableSequence =  Integer.parseInt(results.next().toString());
            }

            tx.commit();
        } catch (Exception e){
            hsession.close();
            throw e;
        }
        return ++availableSequence;

    }
}
