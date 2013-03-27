/**
 * POJO to interact with criteria_areas table. It also contains a method to
 * validate the name and sequence fields.
 */

package edu.osu.cws.evals.models;

import java.util.*;

public class CriterionArea extends Evals {

    private int id = 0;

    private String name = "";

    private String appointmentType;

    private CriterionArea originalID;

    private int sequence;

    private Date createDate;

    private Employee creator;

    private Date deleteDate;

    private Employee deleter;

    private Set details = new HashSet();

    public CriterionArea() { }

    /**
     * Method called by util Hibernate classes to validate the name.
     *
     * @return errors
     */
    public boolean validateName() {
        ArrayList<String> nameErrors = new ArrayList<String>();

        // If there were any previous validation errors remove them.
        this.errors.remove("name");
        if (this.name == null || this.name.equals("")) {
            nameErrors.add(super.resources.getString("criteria-nameRequired"));
        }

        if (nameErrors.size() > 0) {
            this.errors.put("name", nameErrors);
            return false;
        }
        return true;
    }

    /**
     * Method called by util Hibernate classes to validate the sequence.
     *
     * @return errors
     */
    public boolean validateSequence() {
        ArrayList<String> sequenceErrors = new ArrayList<String>();

        // If there were any previous validation errors remove them.
        this.errors.remove("sequence");
        if (this.sequence == 0) {
            sequenceErrors.add("");
        } else if (this.sequence < 1) {
            sequenceErrors.add("");
        }

        if (sequenceErrors.size() > 0) {
            this.errors.put("sequence", sequenceErrors);
            return false;
        }
        return true;
    }

    /**
     * Method called by util Hibernate classes to make sure that there is a
     * valid appointment type set.
     *
     * @return
     */
    public boolean validateAppointmentType() {
        ArrayList<String> appointmentErrors = new ArrayList<String>();

        // If there were any previous validation errors remove them.
        this.errors.remove("appointmentType");
        if (this.appointmentType == null) {
            appointmentErrors.add(super.resources.getString("criteria-appointmentTypeRequired"));
        } else if (this.appointmentType.equals("")) {
            appointmentErrors.add(super.resources.getString("criteria-appointmentTypeRequired"));
        }

        if (appointmentErrors.size() > 0) {
            this.errors.put("appointmentType", appointmentErrors);
            return false;
        }
        return true;
    }


    /**
     * Returns the most recent criterion_detail. The sorting is done by the
     * db using the createDate field.
     *
     * @return  The most recently created CriterionDetail for the CriterionArea
     */
    public CriterionDetail getCurrentDetail() {
        return (CriterionDetail) details.toArray()[0];
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(String appointmentType) {
        this.appointmentType = appointmentType;
    }

    public CriterionArea getOriginalID() {
        return originalID;
    }

    public void setOriginalID(CriterionArea originalID) {
        this.originalID = originalID;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Employee getCreator() {
        return creator;
    }

    public void setCreator(Employee creator) {
        this.creator = creator;
    }

    public Date getDeleteDate() {
        return deleteDate;
    }

    public void setDeleteDate(Date deleteDate) {
        this.deleteDate = deleteDate;
    }

    public Employee getDeleter() {
        return deleter;
    }

    public void setDeleter(Employee deleter) {
        this.deleter = deleter;
    }

    public Set getDetails() {
        return details;
    }

    public void setDetails(Set details) {
        this.details = details;
    }

    /**
     * This method is not needed by hibernate, but it is a helper method used to add CriterionDetail
     * to the details HashSet.
     *
     * @param detail
     */
    public void addDetails(CriterionDetail detail) {
        detail.setAreaID(this);
        this.details.add(detail);
    }

}
