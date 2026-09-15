package com.jacolp.system.application.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailChangeRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "原邮箱不能为空")
    @Email(message = "原邮箱格式不正确")
    private String oldEmail;

    @NotBlank(message = "新邮箱不能为空")
    @Email(message = "新邮箱格式不正确")
    private String newEmail;
}
