package com.banking.beneficiary.dto.request;

import com.banking.beneficiary.validation.ValidIFSC;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddBeneficiaryRequest {

    @NotBlank
    @Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be 9-18 digits")
    private String accountNumber;

    @NotBlank
    @ValidIFSC
    private String ifscCode;

    @NotBlank
    @Size(max = 100)
    private String beneficiaryName;

    @Size(max = 100)
    private String bankName;

    @Size(max = 50)
    private String nickName;
}
