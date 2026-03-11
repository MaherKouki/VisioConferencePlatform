package com.example.auth_service.dto;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Le nom d'utilisateur est requis")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Seulement lettres, chiffres et underscore")
    private String username;

    @NotBlank(message = "L'email est requis")
    @Email(message = "Format email invalide")
    private String email;

    @NotBlank(message = "Le prénom est requis")
    @Size(min = 2, max = 50)
    private String firstName;

    @NotBlank(message = "Le nom est requis")
    @Size(min = 2, max = 50)
    private String lastName;

    @NotBlank(message = "Le mot de passe est requis")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;
}
