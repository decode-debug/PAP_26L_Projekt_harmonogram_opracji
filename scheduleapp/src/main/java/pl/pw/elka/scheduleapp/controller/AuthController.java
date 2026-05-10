package pl.pw.elka.scheduleapp.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.pw.elka.scheduleapp.dto.AuthResponseDTO;
import pl.pw.elka.scheduleapp.dto.LoginRequestDTO;
import pl.pw.elka.scheduleapp.dto.RegisterRequestDTO;
import pl.pw.elka.scheduleapp.model.AppUser;
import pl.pw.elka.scheduleapp.repository.UserRepository;
import pl.pw.elka.scheduleapp.security.JwtService;
import pl.pw.elka.scheduleapp.security.RsaKeyService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private RsaKeyService rsaKeyService;
    @Autowired private PasswordEncoder passwordEncoder;

    /** Returns the server's RSA public key (Base64 DER) so the client can encrypt the password. */
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", rsaKeyService.getPublicKeyBase64()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().body("Imię i nazwisko jest wymagane.");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("Adres e-mail jest wymagany.");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            return ResponseEntity.badRequest().body("Konto z tym adresem e-mail już istnieje.");
        }
        if (dto.getEncryptedPassword() == null || dto.getEncryptedPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Hasło jest wymagane.");
        }
        try {
            String plainPassword = rsaKeyService.decrypt(dto.getEncryptedPassword());
            if (plainPassword.length() < 6) {
                return ResponseEntity.badRequest().body("Hasło musi mieć co najmniej 6 znaków.");
            }
            AppUser user = new AppUser();
            user.setName(dto.getName());
            user.setEmail(dto.getEmail().toLowerCase().trim());
            user.setPasswordHash(passwordEncoder.encode(plainPassword));
            userRepository.save(user);
            String token = jwtService.generateToken(user.getUuid());
            return ResponseEntity.ok(new AuthResponseDTO(token, user.getUuid(), user.getName(), user.getEmail()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Błąd rejestracji: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        if (dto.getEmail() == null || dto.getEncryptedPassword() == null) {
            return ResponseEntity.badRequest().body("Email i hasło są wymagane.");
        }
        Optional<AppUser> userOpt = userRepository.findByEmail(dto.getEmail().toLowerCase().trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Nieprawidłowy adres e-mail lub hasło.");
        }
        try {
            String plainPassword = rsaKeyService.decrypt(dto.getEncryptedPassword());
            AppUser user = userOpt.get();
            if (!passwordEncoder.matches(plainPassword, user.getPasswordHash())) {
                return ResponseEntity.status(401).body("Nieprawidłowy adres e-mail lub hasło.");
            }
            String token = jwtService.generateToken(user.getUuid());
            return ResponseEntity.ok(new AuthResponseDTO(token, user.getUuid(), user.getName(), user.getEmail()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Błąd logowania.");
        }
    }
}
