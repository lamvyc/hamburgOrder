package com.burgerlab.platform.health;

/**
 * Flat row returned by the MyBatis probe query. The field names are mapped
 * from snake_case columns by MyBatis-Plus configuration.
 */
public class SystemProbeRow {

    private String schemaReady;
    private String flywayVersion;
    private String flywayDescription;

    public String getSchemaReady() {
        return schemaReady;
    }

    public void setSchemaReady(String schemaReady) {
        this.schemaReady = schemaReady;
    }

    public String getFlywayVersion() {
        return flywayVersion;
    }

    public void setFlywayVersion(String flywayVersion) {
        this.flywayVersion = flywayVersion;
    }

    public String getFlywayDescription() {
        return flywayDescription;
    }

    public void setFlywayDescription(String flywayDescription) {
        this.flywayDescription = flywayDescription;
    }
}
