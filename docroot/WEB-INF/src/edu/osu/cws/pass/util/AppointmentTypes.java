package edu.osu.cws.pass.util;

import edu.osu.cws.pass.models.AppointmentType;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class AppointmentTypes {

    /**
     * Uses hibernate to fetch and return a list of AppointmentType hibernate POJOs.
     *
     * @return
     */
    public List list() {
        Session hsession = HibernateUtil.getCurrentSession();
        Transaction tx = hsession.beginTransaction();
        List result = hsession.createQuery("from edu.osu.cws.pass.models.AppointmentType").list();
        tx.commit();
        return result;
    }

}
