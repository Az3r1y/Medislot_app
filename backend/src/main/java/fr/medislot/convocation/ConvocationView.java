package fr.medislot.convocation;

// Valeurs déjà formatées : le modèle ne fait aucun calcul.
public record ConvocationView(
        Long number,
        String day,
        String time,
        String professional,
        String specialty,
        String address,
        String city) {}
