package com.inventory.model;

public enum Role {
    /** Full access to every feature. */
    ADMIN("Inventory Admin"),
    /** Can only sell products and view sales records. */
    ACCOUNTANT("Accountant");

    private final String label;
    Role(String label) { this.label = label; }
    public String getLabel() { return label; }
}
