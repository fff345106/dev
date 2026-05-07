package com.example.hello.dto;

public class MasterAuthRequest {
    private String certificationUrl;
    private String representativeWorkUrl;

    public String getCertificationUrl() { return certificationUrl; }
    public void setCertificationUrl(String certificationUrl) { this.certificationUrl = certificationUrl; }
    public String getRepresentativeWorkUrl() { return representativeWorkUrl; }
    public void setRepresentativeWorkUrl(String representativeWorkUrl) { this.representativeWorkUrl = representativeWorkUrl; }
}
