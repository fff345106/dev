package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EnterpriseAuthRequest {
    @NotBlank(message = "营业执照照片不能为空")
    private String businessLicenseUrl;

    private String authorizationLetterUrl;

    @NotBlank(message = "法人姓名不能为空")
    @Size(max = 50, message = "法人姓名长度不能超过50")
    private String legalRepresentativeName;

    private Boolean isLegalRepresentative;

    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }
    public String getAuthorizationLetterUrl() { return authorizationLetterUrl; }
    public void setAuthorizationLetterUrl(String authorizationLetterUrl) { this.authorizationLetterUrl = authorizationLetterUrl; }
    public String getLegalRepresentativeName() { return legalRepresentativeName; }
    public void setLegalRepresentativeName(String legalRepresentativeName) { this.legalRepresentativeName = legalRepresentativeName; }
    public Boolean getIsLegalRepresentative() { return isLegalRepresentative; }
    public void setIsLegalRepresentative(Boolean isLegalRepresentative) { this.isLegalRepresentative = isLegalRepresentative; }
}
