package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvVar {
    private String key;
    private String value;
}
