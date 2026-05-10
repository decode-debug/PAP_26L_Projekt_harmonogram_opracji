package pl.pw.elka.scheduleapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {
    private String email;
    /** RSA-OAEP encrypted password, Base64-encoded. */
    private String encryptedPassword;
}
