package org.ecom.customer;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.ecom.customer.AddressService.AddressView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/addresses")
class AddressController {

    private final AddressService addresses;

    AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    List<AddressView> list(@AuthenticationPrincipal Jwt jwt) {
        return addresses.list(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddressView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PostalAddress address) {
        return addresses.create(jwt.getSubject(), address);
    }

    @PutMapping("/{id}")
    AddressView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PostalAddress address) {
        return addresses.update(jwt.getSubject(), id, address);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        addresses.delete(jwt.getSubject(), id);
    }

}
