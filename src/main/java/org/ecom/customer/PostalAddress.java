package org.ecom.customer;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Embeddable
public record PostalAddress(@NotBlank @Size(max = 200) String fullName,
                            @NotBlank @Size(max = 200) String line1,
                            @Size(max = 200) String line2,
                            @NotBlank @Size(max = 20) String postalCode,
                            @NotBlank @Size(max = 100) String city,
                            /* ISO 3166-1 alpha-2 */
                            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country,
                            @Size(max = 30) @Pattern(regexp = "^\\+?[0-9 .()-]*$") String phone) {
}
