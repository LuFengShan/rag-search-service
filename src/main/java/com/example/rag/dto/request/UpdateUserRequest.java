
package com.example.rag.dto.request;

import com.example.rag.entity.User.Role;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String password;

    private Role role;
}
