package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String email;
    // null pour un compte créé avec Google
    public String password;
    public String name;
    public String role;
    // identifiant « sub » du compte Google relié, null sinon
    public String googleSubject;
}
