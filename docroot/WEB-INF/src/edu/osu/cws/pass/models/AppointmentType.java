/**
 * POJO to interact with appointment types. This class is
 * sometimes referred as employment_type. Some sample
 * appointment types are: classified, classified it. This class
 * does not include logic, only static fields to reference the
 * names of the appointment types.
 */
package edu.osu.cws.pass.models;


public class AppointmentType extends Pass {
    public static final String CLASSIFIED = "classified";

    public static final String CLASSIFIED_IT = "classifiedIT";

    private String name;

    public AppointmentType() { }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
