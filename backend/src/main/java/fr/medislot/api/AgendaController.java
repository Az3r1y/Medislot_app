package fr.medislot.api;

import fr.medislot.repository.AppointmentRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgendaController {
    private final AppointmentRepository appointments;
    private final AppointmentController details;

    public AgendaController(AppointmentRepository appointments, AppointmentController details) {
        this.appointments = appointments;
        this.details = details;
    }

    @GetMapping("/api/clinic/agenda")
    @Transactional(readOnly = true)
    public List<AppointmentController.AppointmentDto> agenda() {
        return appointments.findAll().stream().map(details::dto).toList();
    }
}
