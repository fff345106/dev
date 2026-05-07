package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RealNameAuthRequest {
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "\\d{17}[\\dXx]", message = "身份证号格式不正确")
    private String idCardNumber;

    @NotBlank(message = "身份证正面照片不能为空")
    private String idCardFrontUrl;

    @NotBlank(message = "身份证背面照片不能为空")
    private String idCardBackUrl;

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getIdCardFrontUrl() { return idCardFrontUrl; }
    public void setIdCardFrontUrl(String idCardFrontUrl) { this.idCardFrontUrl = idCardFrontUrl; }
    public String getIdCardBackUrl() { return idCardBackUrl; }
    public void setIdCardBackUrl(String idCardBackUrl) { this.idCardBackUrl = idCardBackUrl; }
}
