package fr.medislot.api;

import fr.medislot.tenant.ClinicDirectory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
public class ClinicController {
    private final ClinicDirectory clinics;

    public ClinicController(ClinicDirectory clinics) {
        this.clinics = clinics;
    }

    @GetMapping("/api/clinics")
    public List<ClinicDirectory.Clinic> list(Principal user) {
        return clinics.list(user == null ? null : user.getName());
    }
}
