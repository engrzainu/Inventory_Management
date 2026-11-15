package com.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "business_settings")
public class BusinessSettings {
    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String businessName = "My Shop";

    @Column(length = 255)
    private String address = "";

    @Column(length = 50)
    private String phone = "";

    /** Logo stored as a small data URL (data:image/png;base64,...). */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String logoData;

    public BusinessSettings() {}
    public BusinessSettings(Long id) { this.id = id; }

    public Long getId() { return id; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLogoData() { return logoData; }
    public void setLogoData(String logoData) { this.logoData = logoData; }
}
