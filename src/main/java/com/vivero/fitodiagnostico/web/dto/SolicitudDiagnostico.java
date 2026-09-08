package com.vivero.fitodiagnostico.web.dto;

import jakarta.validation.constraints.*;

public record SolicitudDiagnostico(

        @NotBlank @Size(min = 2, max = 120)
        String especie,

        @NotNull @DecimalMin("-20.0") @DecimalMax("60.0")
        Double temperaturaC,

        @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
        Double humedadRelativa,

        @NotNull @Min(0) @Max(150_000)
        Integer luzLux) { }
